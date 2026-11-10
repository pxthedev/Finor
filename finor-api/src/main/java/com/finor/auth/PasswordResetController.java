package com.finor.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.context.annotation.Profile("!firestore")
@RestController
@RequestMapping("/api/v1/auth")
public class PasswordResetController {
  private final JdbcTemplate db;
  private final ObjectProvider<JavaMailSender> mail;
  private final BCryptPasswordEncoder passwords;
  private final String from;

  public PasswordResetController(
      JdbcTemplate db,
      ObjectProvider<JavaMailSender> mail,
      BCryptPasswordEncoder passwords,
      @Value("${MAIL_FROM:}") String from) {
    this.db = db;
    this.mail = mail;
    this.passwords = passwords;
    this.from = from;
  }

  public record Forgot(@NotBlank @Email @Size(max = 254) String email) {}

  public record Reset(
      @NotBlank @Size(max = 200) String token,
      @NotBlank @Size(min = 8, max = 72) String password) {}

  String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @PostMapping("/forgot-password")
  public Map<String, String> forgot(@Valid @RequestBody Forgot r) {
    var sender = mail.getIfAvailable();
    if (sender == null || from.isBlank())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Recuperação por e-mail ainda não está configurada.");
    var rows =
        db.queryForList(
            "SELECT id FROM app_user WHERE email=?", r.email().trim().toLowerCase(Locale.ROOT));
    if (!rows.isEmpty()) {
      byte[] bytes = new byte[32];
      new SecureRandom().nextBytes(bytes);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      db.update(
          "INSERT INTO password_reset(id,user_id,token_hash,expires_at) VALUES(?,?,?,?)",
          UUID.randomUUID(),
          rows.getFirst().get("id"),
          hash(token),
          java.sql.Timestamp.from(Instant.now().plusSeconds(1200)));
      var message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(r.email());
      message.setSubject("Recuperação de acesso ao Finor");
      message.setText(
          "Use este código no Finor para definir uma nova senha. Ele expira em 20 minutos:\n\n"
              + token
              + "\n\nSe você não solicitou, ignore este e-mail.");
      sender.send(message);
    }
    return Map.of("message", "Se o e-mail estiver cadastrado, você receberá as instruções.");
  }

  @Transactional
  @PostMapping("/reset-password")
  public void reset(@Valid @RequestBody Reset r) {
    var rows =
        db.queryForList(
            "SELECT * FROM password_reset WHERE token_hash=? AND used=false AND expires_at>now()"
                + " FOR UPDATE",
            hash(r.token()));
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido ou expirado.");
    var user = rows.getFirst().get("user_id");
    db.update(
        "UPDATE app_user SET password_hash=? WHERE id=?", passwords.encode(r.password()), user);
    db.update("UPDATE password_reset SET used=true WHERE user_id=?", user);
    db.update("UPDATE refresh_token SET revoked=true WHERE user_id=?", user);
  }
}

