package com.finor.firestore;

import static com.finor.firestore.FirestoreFinance.field;
import static com.finor.firestore.FirestoreStore.*;

import com.fasterxml.jackson.databind.*;
import com.finor.assistant.AssistantController.Message;
import com.finor.assistant.AssistantProvider;
import com.finor.assistant.AssistantCategory;
import com.finor.transaction.FinanceController.Transaction;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("firestore")
@RequestMapping("/api/v1/assistant")
public class FirestoreAssistant {
  private final FirestoreStore store;
  private final FirestoreFinance finance;
  private final AssistantProvider provider;
  private final ObjectMapper json;
  private final Validator validator;

  public FirestoreAssistant(
      FirestoreStore store,
      FirestoreFinance finance,
      AssistantProvider provider,
      ObjectMapper json,
      Validator validator) {
    this.store = store;
    this.finance = finance;
    this.provider = provider;
    this.json = json;
    this.validator = validator;
  }

  @GetMapping("/sessions")
  public Object sessions(@AuthenticationPrincipal Jwt j) {
    return store.view(
        j.getSubject(),
        List.of("chats"),
        false,
        s ->
            s.rows("chats").stream()
                .sorted(
                    Comparator.<Map<String, Object>, Instant>comparing(
                            c -> Instant.parse(field(c, "created_at")))
                        .reversed())
                .toList());
  }

  @GetMapping("/sessions/{id}/messages")
  public Object messages(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    return store.view(
        j.getSubject(),
        List.of("chats", "messages"),
        false,
        s -> {
          s.require("chats", id.toString());
          return s.rows("messages").stream()
              .filter(m -> id.toString().equals(field(m, "session_id")))
              .sorted(Comparator.comparing(m -> Instant.parse(field(m, "created_at"))))
              .toList();
        });
  }

  @GetMapping("/sessions/{id}/actions")
  public Object actions(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    return store.view(
        j.getSubject(),
        List.of("chats", "actions"),
        false,
        s -> {
          s.require("chats", id.toString());
          return s.rows("actions").stream()
              .filter(a -> id.toString().equals(field(a, "session_id")))
              .toList();
        });
  }

  @GetMapping("/sessions/{id}/timeline")
  public Object timeline(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    return store.view(j.getSubject(), List.of("chats", "messages", "actions"), false, s -> {
      s.require("chats", id.toString());
      return row("messages", s.rows("messages").stream()
          .filter(m -> id.toString().equals(field(m, "session_id")))
          .sorted(Comparator.comparing(m -> Instant.parse(field(m, "created_at")))).toList(),
          "actions", s.rows("actions").stream().filter(a -> id.toString().equals(field(a, "session_id"))).toList());
    });
  }

  @PostMapping("/messages")
  public Object send(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Message r)
      throws Exception {
    String owner = j.getSubject(),
        session = r.sessionId() == null ? id() : r.sessionId().toString();
    String hour = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS).toString();
    store.view(
        owner,
        List.of("chats", "chat_quota"),
        true,
        s -> {
          if (r.sessionId() != null) s.require("chats", session);
          var quota = s.table("chat_quota").get("current");
          long count =
              quota != null && hour.equals(quota.get("hour"))
                  ? ((Number) quota.get("count")).longValue()
                  : 0;
          if (count >= 30) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
          s.put("chat_quota", "current", row("hour", hour, "count", count + 1));
          if (r.sessionId() == null)
            s.put("chats", session, row("id", session, "created_at", Instant.now().toString()));
          return null;
        });
    JsonNode intent;
    var knownCategories = finance.categories(owner);
    try {
      intent = provider.interpret(r.message(), knownCategories.stream().map(c -> field(c, "name")).toList());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Não foi possível interpretar a mensagem.");
    }
    String reply;
    Transaction proposal = null;
    switch (intent.path("intent").asText()) {
      case "available" -> {
        var a = finance.available(owner);
        reply =
            "Disponível até o próximo recebimento: R$ "
                + a.available()
                + ". Limite diário sugerido: R$ "
                + a.suggestedDaily()
                + ". Cálculo baseado nas previsões registradas.";
      }
      case "expenses" -> {
        String name = intent.path("category").asText("");
        if (name.isBlank()) {
          reply =
              "Despesas registradas neste mês: R$ "
                  + finance.summary(owner, null).get("expenses")
                  + ".";
        } else {
          var categories =
              finance.categoryReport(owner, YearMonth.from(FirestoreFinance.today()).toString());
          var category =
              categories.stream().filter(c -> field(c, "name").equalsIgnoreCase(name)).findFirst();
          reply =
              category.isPresent()
                  ? "Despesas com "
                      + category.get().get("name")
                      + " neste mês: R$ "
                      + category.get().get("spent")
                      + "."
                  : "Não encontrei essa categoria nos seus registros.";
        }
      }
      case "simulate" -> {
        BigDecimal amount = intent.path("amount").decimalValue();
        if (amount.signum() <= 0 || amount.scale() > 2) {
          reply = "Qual é o valor da compra?";
          break;
        }
        var simulation = finance.simulate(owner, amount);
        reply =
            "Após a compra restariam R$ "
                + simulation.get("remaining")
                + ". "
                + simulation.get("notice");
      }
      case "planning" -> {
        var suggestion = finance.suggestions(owner);
        reply =
            "Renda: R$ "
                + suggestion.get("income")
                + ". Obrigações: R$ "
                + suggestion.get("obligations")
                + ". Essenciais: R$ "
                + suggestion.get("essentials")
                + ". Margem: R$ "
                + suggestion.get("margin")
                + ". Ainda não distribuído: R$ "
                + suggestion.get("unallocated")
                + ". Revise os valores em Gastos e Metas antes de confirmar.";
      }
      case "savings" ->
          reply =
              "Crie uma meta em Metas financeiras e ajuste sua reserva após revisar o disponível."
                  + " Nenhum valor foi reservado automaticamente.";
      case "transaction" -> {
        var accounts = finance.accounts(owner);
        var amount = intent.path("amount").decimalValue();
        String type = intent.path("type").asText("");
        if (accounts.size() != 1
            || amount.signum() <= 0
            || amount.scale() > 2
            || amount.precision() > 19
            || !Set.of("INCOME", "EXPENSE").contains(type)) {
          reply =
              "Informe o valor e se é receita ou despesa. Para escolher entre várias contas, use o"
                  + " registro manual.";
          break;
        }
        String categoryName = intent.path("category").asText("");
        var resolved = AssistantCategory.resolve(categoryName, knownCategories,
            name -> UUID.fromString(field(finance.category(owner, name), "id")));
        UUID category = resolved.id();
        LocalDate date =
            intent.hasNonNull("date")
                ? LocalDate.parse(intent.get("date").asText())
                : FirestoreFinance.today();
        proposal =
            new Transaction(
                UUID.fromString(field(accounts.getFirst(), "id")),
                null,
                category,
                com.finor.planning.Ledger.Type.valueOf(type),
                amount,
                date,
                intent.path("description").asText(""));
        if (!validator.validate(proposal).isEmpty()) throw new IllegalArgumentException();
        reply = "Categoria: " + resolved.name() + ". Confira a transação antes de confirmar. Nenhum valor foi registrado ainda.";
      }
      default ->
          reply =
              "Posso registrar receitas e despesas, consultar seus gastos e simular compras."
                  + " Informe o valor e o tipo para preparar uma transação.";
    }
    final Transaction action = proposal;
    final String text = reply;
    String payload = action == null ? null : json.writeValueAsString(action);
    String actionId = id(),
        userMessage = id(),
        assistantMessage = id(),
        created = Instant.now().toString();
    return store.view(
        owner,
        List.of("chats", "messages", "actions"),
        true,
        s -> {
          s.require("chats", session);
          s.put(
              "messages",
              userMessage,
              row(
                  "id",
                  userMessage,
                  "session_id",
                  session,
                  "role",
                  "user",
                  "content",
                  r.message(),
                  "created_at",
                  created));
          s.put(
              "messages",
              assistantMessage,
              row(
                  "id",
                  assistantMessage,
                  "session_id",
                  session,
                  "role",
                  "assistant",
                  "content",
                  text,
                  "created_at",
                  Instant.parse(created).plusNanos(1).toString()));
          Map<String, Object> result = row("sessionId", session, "reply", text, "action", null);
          if (action != null) {
            s.put(
                "actions",
                actionId,
                row(
                    "id",
                    actionId,
                    "session_id",
                    session,
                    "payload",
                    payload,
                    "status",
                    "PENDING",
                    "created_at",
                    created));
            result.put("action", row("id", actionId, "transaction", action));
          }
          return result;
        });
  }

  @PostMapping("/actions/{id}/confirm")
  public Object confirm(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    var tables = new ArrayList<>(FirestoreFinance.LEDGER);
    tables.add("actions");
    return store.view(
        j.getSubject(),
        tables,
        true,
        s -> {
          var action = s.require("actions", id.toString());
          if ("REJECTED".equals(action.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT);
          try {
            var payload = json.readValue(field(action, "payload"), Transaction.class);
            if (!validator.validate(payload).isEmpty()) throw new IllegalArgumentException();
            var value = FirestoreFinance.create(s, id.toString(), payload);
            action.put("status", "CONFIRMED");
            return value;
          } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException();
          }
        });
  }

  @PostMapping("/actions/{id}/reject")
  public void reject(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    store.view(
        j.getSubject(),
        List.of("actions"),
        true,
        s -> {
          var action = s.require("actions", id.toString());
          if (!"PENDING".equals(action.get("status")))
            throw new ResponseStatusException(HttpStatus.CONFLICT);
          action.put("status", "REJECTED");
          return null;
        });
  }
}

