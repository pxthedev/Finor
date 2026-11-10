package com.finor.firestore;

import static com.finor.firestore.FirestoreStore.*;

import com.finor.auth.PasswordResetController.*;
import jakarta.validation.Valid;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("firestore-legacy-auth")
@RequestMapping("/api/v1/auth")
public class FirestorePasswordReset {
  private final FirestoreStore store;
  private final ObjectProvider<JavaMailSender> mail;
  private final BCryptPasswordEncoder passwords;
  private final String from;

  public FirestorePasswordReset(
      FirestoreStore store,
      ObjectProvider<JavaMailSender> mail,
      BCryptPasswordEncoder passwords,
      @Value("${MAIL_FROM:}") String from) {
    this.store = store;
    this.mail = mail;
    this.passwords = passwords;
    this.from = from;
  }

  @PostMapping("/forgot-password")
  public Object forgot(@Valid @RequestBody Forgot r) {
    var sender = mail.getIfAvailable();
    if (sender == null || from.isBlank())
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Recuperação por e-mail ainda não está configurada.");
    var claim =
        await(
            store
                .database()
                .collection("finor_email_claims")
                .document(hash(r.email().trim().toLowerCase(Locale.ROOT)))
                .get());
    if (claim.exists()) {
      String token = FirestoreAuth.random(), owner = claim.getString("user_id");
      var reference = store.database().collection("finor_password_resets").document(hash(token));
      String email =
          await(
              store
                  .database()
                  .runTransaction(
                      tx -> {
                        var user = tx.get(store.user(owner)).get();
                        if (!user.exists() || !"ACTIVE".equals(user.getString("status")))
                          return null;
                        tx.create(
                            reference,
                            row(
                                "user_id",
                                owner,
                                "auth_version",
                                FirestoreAuth.version(user.getData()),
                                "used",
                                false,
                                "expires_at",
                                Instant.now().plusSeconds(1200).toString()));
                        return user.getString("email");
                      }));
      if (email != null) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Recuperação de acesso ao Finor");
        message.setText(
            "Use este código para definir uma nova senha. Ele expira em 20 minutos:\n\n"
                + token
                + "\n\nSe você não solicitou, ignore este e-mail.");
        sender.send(message);
      }
    }
    return Map.of("message", "Se o e-mail estiver cadastrado, você receberá as instruções.");
  }

  @PostMapping("/reset-password")
  public void reset(@Valid @RequestBody Reset r) {
    String password = passwords.encode(r.password());
    await(
        store
            .database()
            .runTransaction(
                tx -> {
                  var ref =
                      store
                          .database()
                          .collection("finor_password_resets")
                          .document(hash(r.token()));
                  var token = tx.get(ref).get();
                  if (!token.exists()
                      || Boolean.TRUE.equals(token.getBoolean("used"))
                      || Instant.parse(token.getString("expires_at")).isBefore(Instant.now()))
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Código inválido ou expirado.");
                  var userRef = store.user(token.getString("user_id"));
                  var user = tx.get(userRef).get();
                  if (!user.exists()
                      || !"ACTIVE".equals(user.getString("status"))
                      || FirestoreAuth.version(user.getData())
                          != FirestoreAuth.version(token.getData()))
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Código inválido ou expirado.");
                  tx.update(
                      userRef,
                      row(
                          "password_hash",
                          password,
                          "auth_version",
                          FirestoreAuth.version(user.getData()) + 1));
                  tx.update(ref, "used", true);
                  return null;
                }));
  }
}

