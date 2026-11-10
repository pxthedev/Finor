package com.finor.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.context.annotation.Profile("!firestore")
@Service
public class AuthService {
  private final JdbcTemplate db;
  private final BCryptPasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final TransactionTemplate tx;

  public AuthService(
      JdbcTemplate db,
      BCryptPasswordEncoder passwords,
      JwtEncoder encoder,
      PlatformTransactionManager manager) {
    this.db = db;
    this.passwords = passwords;
    this.encoder = encoder;
    tx = new TransactionTemplate(manager);
  }

  public record Session(String accessToken, String refreshToken, long expiresIn) {}

  public Session register(String name, String email, String password) {
    return tx.execute(
        status -> {
          var id = UUID.randomUUID();
          db.update(
              "INSERT INTO app_user(id,name,email,password_hash) VALUES (?,?,?,?)",
              id,
              name.trim(),
              email.toLowerCase(Locale.ROOT).trim(),
              passwords.encode(password));
          return issue(id, UUID.randomUUID());
        });
  }

  public Session login(String email, String password) {
    var rows =
        db.queryForList(
            "SELECT id,password_hash FROM app_user WHERE email=?",
            email.toLowerCase(Locale.ROOT).trim());
    if (rows.isEmpty()
        || !passwords.matches(password, rows.getFirst().get("password_hash").toString()))
      throw unauthorized();
    return issue((UUID) rows.getFirst().get("id"), UUID.randomUUID());
  }

  public Session refresh(String token) {
    Session result =
        tx.execute(
            status -> {
              var rows =
                  db.queryForList(
                      "SELECT * FROM refresh_token WHERE token_hash=? FOR UPDATE", hash(token));
              if (rows.isEmpty()) return null;
              var row = rows.getFirst();
              var family = (UUID) row.get("family_id");
              if ((Boolean) row.get("revoked")
                  || ((java.sql.Timestamp) row.get("expires_at"))
                      .toInstant()
                      .isBefore(Instant.now())) {
                db.update("UPDATE refresh_token SET revoked=true WHERE family_id=?", family);
                return null;
              }
              db.update("UPDATE refresh_token SET revoked=true WHERE id=?", row.get("id"));
              return issue((UUID) row.get("user_id"), family);
            });
    if (result == null) throw unauthorized();
    return result;
  }

  public void logout(String token) {
    db.update(
        "UPDATE refresh_token SET revoked=true WHERE family_id IN (SELECT family_id FROM"
            + " refresh_token WHERE token_hash=?)",
        hash(token));
  }

  private Session issue(UUID user, UUID family) {
    var now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer("finor")
            .subject(user.toString())
            .claim("sid", family.toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .build();
    var access =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update(
        "INSERT INTO refresh_token(id,user_id,family_id,token_hash,expires_at) VALUES(?,?,?,?,?)",
        UUID.randomUUID(),
        user,
        family,
        hash(refresh),
        java.sql.Timestamp.from(now.plus(Duration.ofDays(30))));
    return new Session(access, refresh, 600);
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais ou sessão inválidas.");
  }
}

