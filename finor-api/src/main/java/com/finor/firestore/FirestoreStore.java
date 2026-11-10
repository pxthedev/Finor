package com.finor.firestore;

import com.google.cloud.firestore.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

/** Native Firestore persistence. Every resource path is scoped to its authenticated owner. */
@Repository
@Profile("firestore")
public class FirestoreStore {
  private final Firestore db;

  public FirestoreStore(Firestore db) {
    this.db = db;
  }

  public Firestore database() {
    return db;
  }

  public DocumentReference user(String owner) {
    UUID.fromString(owner);
    return db.collection("finor_users").document(owner);
  }

  static <T> T await(Future<T> future) {
    try {
      return future.get(45, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Operação interrompida.");
    } catch (Exception e) {
      Throwable cause = e;
      while (cause.getCause() != null) cause = cause.getCause();
      if (cause instanceof ResponseStatusException r) throw r;
      if (cause instanceof IllegalArgumentException r) throw r;
      org.slf4j.LoggerFactory.getLogger(FirestoreStore.class)
          .warn(
              "Falha Firestore: tipo={}, grpc={}",
              cause.getClass().getSimpleName(),
              io.grpc.Status.fromThrowable(e).getCode());
      if (System.getenv("FINOR_FIRESTORE_TESTS") != null)
        org.slf4j.LoggerFactory.getLogger(FirestoreStore.class)
            .debug("Diagnóstico exclusivo do emulador: {}", cause.getMessage());
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "O banco financeiro está temporariamente indisponível.");
    }
  }

  public <T> T view(String owner, List<String> tables, boolean write, Function<State, T> body) {
    return await(
        db.runTransaction(
            tx -> {
              var root = user(owner);
              var snapshot = tx.get(root).get();
              if (!snapshot.exists() || !"ACTIVE".equals(snapshot.getString("status")))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
              var state = new State(new LinkedHashMap<>(snapshot.getData()));
              var original = new LinkedHashMap<String, Map<String, Map<String, Object>>>();
              // Start independent reads together while retaining the transaction's snapshot.
              var pending = new LinkedHashMap<String, com.google.api.core.ApiFuture<QuerySnapshot>>();
              for (String table : new LinkedHashSet<>(tables))
                pending.put(table, tx.get(root.collection(table)));
              for (String table : pending.keySet()) {
                var rows = new LinkedHashMap<String, Map<String, Object>>();
                for (var doc : pending.get(table).get().getDocuments())
                  rows.put(doc.getId(), new LinkedHashMap<>(doc.getData()));
                original.put(table, rows);
                var copy = new LinkedHashMap<String, Map<String, Object>>();
                rows.forEach((id, row) -> copy.put(id, new LinkedHashMap<>(row)));
                state.tables.put(table, copy);
              }
              T result = body.apply(state);
              if (write) {
                var changes = new ArrayList<Runnable>();
                for (String table : original.keySet()) {
                  var before = original.get(table);
                  var after = state.table(table);
                  for (String id : before.keySet())
                    if (!after.containsKey(id)) {
                      var ref = root.collection(table).document(id);
                      changes.add(() -> tx.delete(ref));
                    }
                  for (var entry : after.entrySet()) {
                    var normalized = encode(entry.getValue());
                    if (!Objects.equals(before.get(entry.getKey()), normalized)) {
                      var ref = root.collection(table).document(entry.getKey());
                      changes.add(() -> tx.set(ref, normalized));
                    }
                  }
                }
                if (changes.size() > 440)
                  throw new ResponseStatusException(
                      HttpStatus.CONFLICT,
                      "A operação excede o limite de alterações. Divida em operações menores.");
                // The root document serializes mutations across collections and prevents deletion
                // races.
                if (!changes.isEmpty() || !Objects.equals(snapshot.getData(), encode(state.profile))) {
                  state.profile.put("revision", UUID.randomUUID().toString());
                  tx.set(root, encode(state.profile));
                  changes.forEach(Runnable::run);
                }
              }
              return result;
            }));
  }

  static final Set<String> MONEY =
      Set.of(
          "amount",
          "opening_balance",
          "balance",
          "reserved",
          "target",
          "monthly_amount",
          "installment_amount",
          "remaining_essentials",
          "safety_margin",
          "spent",
          "income",
          "expenses");

  @SuppressWarnings("unchecked")
  static Object encode(Object value) {
    if (value instanceof BigDecimal d) return d.setScale(2).toPlainString();
    if (value instanceof UUID || value instanceof java.time.temporal.TemporalAccessor)
      return value.toString();
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<String, Object>();
      map.forEach((k, v) -> result.put(k.toString(), encode(v)));
      return result;
    }
    if (value instanceof List<?> list) return list.stream().map(FirestoreStore::encode).toList();
    return value;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> wire(Map<String, Object> row) {
    var out = new LinkedHashMap<String, Object>();
    row.forEach(
        (k, v) -> out.put(k, v != null && MONEY.contains(k) ? new BigDecimal(v.toString()) : v));
    return out;
  }

  static BigDecimal money(Map<String, Object> row, String key) {
    Object value = row.get(key);
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  static Map<String, Object> row(Object... fields) {
    var row = new LinkedHashMap<String, Object>();
    for (int i = 0; i < fields.length; i += 2) row.put(fields[i].toString(), fields[i + 1]);
    return row;
  }

  static String id() {
    return UUID.randomUUID().toString();
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
  }

  static class State {
    final Map<String, Object> profile;
    final Map<String, Map<String, Map<String, Object>>> tables = new LinkedHashMap<>();

    State(Map<String, Object> profile) {
      this.profile = profile;
    }

    Map<String, Map<String, Object>> table(String name) {
      var table = tables.get(name);
      if (table == null) throw new IllegalStateException("Coleção não carregada.");
      return table;
    }

    List<Map<String, Object>> rows(String name) {
      return new ArrayList<>(table(name).values());
    }

    Map<String, Object> require(String table, String id) {
      var row = table(table).get(id);
      if (row == null) throw missing();
      return row;
    }

    void put(String table, String id, Map<String, Object> row) {
      table(table).put(id, row);
    }
  }
}
