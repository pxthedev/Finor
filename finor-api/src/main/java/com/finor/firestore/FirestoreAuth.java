package com.finor.firestore;

import static com.finor.firestore.FirestoreStore.*;

import com.google.cloud.firestore.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("firestore-legacy-auth")
public class FirestoreAuth {
  private final FirestoreStore store;
  private final BCryptPasswordEncoder passwords;
  private final JwtEncoder encoder;

  public FirestoreAuth(FirestoreStore store, BCryptPasswordEncoder passwords, JwtEncoder encoder) {
    this.store = store;
    this.passwords = passwords;
    this.encoder = encoder;
  }

  public record Session(String accessToken, String refreshToken, long expiresIn) {}

  public Session register(String name, String email, String password) {
    String owner = id(),
        normalized = email.trim().toLowerCase(Locale.ROOT),
        hash = passwords.encode(password);
    return await(
        store
            .database()
            .runTransaction(
                tx -> {
                  var claim =
                      store.database().collection("finor_email_claims").document(hash(normalized));
                  if (tx.get(claim).get().exists())
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
                  tx.create(claim, row("user_id", owner));
                  tx.create(
                      store.user(owner),
                      row(
                          "id",
                          owner,
                          "name",
                          name.trim(),
                          "email",
                          normalized,
                          "password_hash",
                          hash,
                          "onboarded",
                          false,
                          "auth_version",
                          0L,
                          "status",
                          "ACTIVE",
                          "created_at",
                          Instant.now().toString()));
                  return issue(tx, owner, id(), 0L);
                }));
  }

  public Session login(String email, String password) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    var claim =
        await(store.database().collection("finor_email_claims").document(hash(normalized)).get());
    if (!claim.exists()) throw unauthorized();
    String owner = claim.getString("user_id");
    var profile = await(store.user(owner).get());
    if (!profile.exists()
        || !"ACTIVE".equals(profile.getString("status"))
        || !passwords.matches(password, profile.getString("password_hash"))) throw unauthorized();
    return await(
        store
            .database()
            .runTransaction(
                tx -> {
                  var user = tx.get(store.user(owner)).get();
                  if (!user.exists()
                      || !"ACTIVE".equals(user.getString("status"))
                      || !Objects.equals(
                          profile.getString("password_hash"), user.getString("password_hash")))
                    throw unauthorized();
                  return issue(tx, owner, id(), version(user.getData()));
                }));
  }

  private Session issue(Transaction tx, String owner, String family, long version) {
    var now = Instant.now();
    String refresh = random();
    String hashed = hash(refresh);
    var claims =
        JwtClaimsSet.builder()
            .issuer("finor")
            .subject(owner)
            .claim("sid", family)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .build();
    String access =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    tx.set(
        store.user(owner).collection("sessions").document(family),
        row(
            "user_id",
            owner,
            "auth_version",
            version,
            "revoked",
            false,
            "expires_at",
            now.plus(Duration.ofDays(30)).toString()));
    tx.create(
        store.database().collection("finor_refresh_tokens").document(hashed),
        row(
            "user_id",
            owner,
            "family_id",
            family,
            "used",
            false,
            "expires_at",
            now.plus(Duration.ofDays(30)).toString()));
    return new Session(access, refresh, 600);
  }

  public Session refresh(String token) {
    var result =
        await(
            store
                .database()
                .runTransaction(
                    tx -> {
                      var reference =
                          store.database().collection("finor_refresh_tokens").document(hash(token));
                      var current = tx.get(reference).get();
                      if (!current.exists()) return null;
                      String owner = current.getString("user_id"),
                          family = current.getString("family_id");
                      var profile = tx.get(store.user(owner)).get();
                      var familyRef = store.user(owner).collection("sessions").document(family);
                      var session = tx.get(familyRef).get();
                      if (!profile.exists()
                          || !"ACTIVE".equals(profile.getString("status"))
                          || !session.exists()) return null;
                      if (version(profile.getData()) != version(session.getData())
                          || Boolean.TRUE.equals(current.getBoolean("used"))
                          || Boolean.TRUE.equals(session.getBoolean("revoked"))
                          || Instant.parse(current.getString("expires_at"))
                              .isBefore(Instant.now())) {
                        tx.update(familyRef, "revoked", true);
                        return null;
                      }
                      tx.update(reference, "used", true);
                      return issue(tx, owner, family, version(profile.getData()));
                    }));
    if (result == null) throw unauthorized();
    return result;
  }

  public void logout(String token) {
    await(
        store
            .database()
            .runTransaction(
                tx -> {
                  var record =
                      tx.get(
                              store
                                  .database()
                                  .collection("finor_refresh_tokens")
                                  .document(hash(token)))
                          .get();
                  if (record.exists()) {
                    var family =
                        store
                            .user(record.getString("user_id"))
                            .collection("sessions")
                            .document(record.getString("family_id"));
                    var snapshot = tx.get(family).get();
                    if (snapshot.exists()) tx.update(family, "revoked", true);
                  }
                  return null;
                }));
  }

  public boolean active(String owner, String family) {
    try {
      var p = await(store.user(owner).get());
      var s = await(store.user(owner).collection("sessions").document(family).get());
      return p.exists()
          && "ACTIVE".equals(p.getString("status"))
          && s.exists()
          && version(p.getData()) == version(s.getData())
          && !Boolean.TRUE.equals(s.getBoolean("revoked"))
          && Instant.parse(s.getString("expires_at")).isAfter(Instant.now());
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  static long version(Map<String, Object> data) {
    return data.get("auth_version") instanceof Number n ? n.longValue() : 0L;
  }

  static String random() {
    byte[] bytes = new byte[48];
    new java.security.SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais ou sessão inválidas.");
  }
}

