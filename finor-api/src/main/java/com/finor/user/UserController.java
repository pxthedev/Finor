package com.finor.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.context.annotation.Profile("!firestore")
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
  private final JdbcTemplate db;
  private final BCryptPasswordEncoder passwords;

  public UserController(JdbcTemplate db, BCryptPasswordEncoder passwords) {
    this.db = db;
    this.passwords = passwords;
  }

  public record Profile(@NotBlank @Size(max = 120) String name) {}

  @PatchMapping
  public void update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Profile r) {
    db.update(
        "UPDATE app_user SET name=? WHERE id=?",
        r.name().trim(),
        UUID.fromString(jwt.getSubject()));
  }

  public record Delete(@NotBlank @Size(max = 72) String password, @AssertTrue boolean confirm) {}

  @GetMapping("/export")
  @Transactional(readOnly = true)
  public Map<String, Object> export(@AuthenticationPrincipal Jwt jwt) {
    var user = UUID.fromString(jwt.getSubject());
    var result = new LinkedHashMap<String, Object>();
    result.put(
        "profile",
        db.queryForMap("SELECT id,name,email,created_at FROM app_user WHERE id=?", user));
    for (String table :
        List.of(
            "budget",
            "budget_allocation",
            "account",
            "category",
            "financial_transaction",
            "financial_goal",
            "installment_plan",
            "recurring_entry",
            "recurring_rule",
            "income_schedule",
            "chat_session",
            "chat_message",
            "assistant_action",
            "user_photo"))
      result.put(table, db.queryForList("SELECT * FROM " + table + " WHERE user_id=?", user));
    return result;
  }

  @DeleteMapping
  @Transactional
  public void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Delete r) {
    var user = UUID.fromString(jwt.getSubject());
    var hash =
        db.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, user);
    if (!passwords.matches(r.password(), hash))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    for (String table :
        List.of(
            "transaction_request",
            "budget_allocation",
            "budget",
            "assistant_action",
            "chat_message",
            "chat_session",
            "financial_transaction",
            "financial_goal",
            "installment_plan",
            "recurring_entry",
            "recurring_rule",
            "income_schedule",
            "category",
            "account",
            "refresh_token",
            "password_reset")) db.update("DELETE FROM " + table + " WHERE user_id=?", user);
    db.update("DELETE FROM app_user WHERE id=?", user);
  }
}
