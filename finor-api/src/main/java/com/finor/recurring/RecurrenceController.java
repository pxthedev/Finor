package com.finor.recurring;

import com.finor.planning.PlanningController.Bill;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.context.annotation.Profile("!firestore")
@RestController
@RequestMapping("/api/v1/recurring/rules")
public class RecurrenceController {
  private final JdbcTemplate db;
  private final RecurrenceService service;

  public RecurrenceController(JdbcTemplate db, RecurrenceService service) {
    this.db = db;
    this.service = service;
  }

  @Transactional
  @PostMapping
  public Map<String, Object> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Bill bill) {
    var user = UUID.fromString(jwt.getSubject());
    var id = UUID.randomUUID();
    db.update(
        "INSERT INTO recurring_rule(id,user_id,name,amount,first_date) VALUES(?,?,?,?,?)",
        id,
        user,
        bill.name(),
        bill.amount(),
        bill.dueDate());
    service.generate(user);
    return Map.of("id", id);
  }

  @Transactional
  @DeleteMapping("/{id}")
  public void stop(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    var user = UUID.fromString(jwt.getSubject());
    if (db.update("UPDATE recurring_rule SET active=false WHERE id=? AND user_id=?", id, user) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    db.update(
        "DELETE FROM recurring_entry WHERE user_id=? AND rule_id=? AND due_date>CURRENT_DATE AND"
            + " paid=false",
        user,
        id);
  }
}

