package com.finor.budget;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
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
@RequestMapping("/api/v1")
public class BudgetController {
  private final JdbcTemplate db;

  public BudgetController(JdbcTemplate db) {
    this.db = db;
  }

  public record Allocation(
      @NotNull UUID categoryId,
      @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

  public record Budget(
      @NotBlank String month, @NotNull @Size(max = 100) List<@Valid Allocation> allocations) {}

  @GetMapping("/budgets")
  public List<Map<String, Object>> budgets(
      @AuthenticationPrincipal Jwt jwt, @RequestParam String month) {
    var start = YearMonth.parse(month).atDay(1);
    return db.queryForList(
        "SELECT a.id,a.category_id,c.name,a.amount,COALESCE((SELECT SUM(t.amount) FROM"
            + " financial_transaction t WHERE t.user_id=a.user_id AND t.category_id=a.category_id"
            + " AND t.type='EXPENSE' AND t.date>=? AND t.date<?),0) AS spent FROM budget_allocation"
            + " a JOIN budget b ON b.id=a.budget_id JOIN category c ON c.id=a.category_id WHERE"
            + " b.user_id=? AND b.month=? ORDER BY c.name",
        start,
        start.plusMonths(1),
        UUID.fromString(jwt.getSubject()),
        start);
  }

  @PostMapping("/budgets")
  @Transactional
  public void save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Budget r) {
    var user = UUID.fromString(jwt.getSubject());
    var month = YearMonth.parse(r.month()).atDay(1);
    var id = UUID.randomUUID();
    db.update(
        "INSERT INTO budget(id,user_id,month) VALUES(?,?,?) ON CONFLICT(user_id,month) DO NOTHING",
        id,
        user,
        month);
    id =
        db.queryForObject(
            "SELECT id FROM budget WHERE user_id=? AND month=? FOR UPDATE",
            UUID.class,
            user,
            month);
    db.update("DELETE FROM budget_allocation WHERE budget_id=? AND user_id=?", id, user);
    var seen = new HashSet<UUID>();
    for (var a : r.allocations()) {
      if (!seen.add(a.categoryId())) throw new IllegalArgumentException();
      if (db.queryForObject(
              "SELECT COUNT(*) FROM category WHERE id=? AND user_id=?",
              Integer.class,
              a.categoryId(),
              user)
          != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
      db.update(
          "INSERT INTO budget_allocation(id,user_id,budget_id,category_id,amount)"
              + " VALUES(?,?,?,?,?)",
          UUID.randomUUID(),
          user,
          id,
          a.categoryId(),
          a.amount());
    }
  }

  @GetMapping("/reports/evolution")
  public List<Map<String, Object>> evolution(
      @AuthenticationPrincipal Jwt jwt, @RequestParam String month) {
    var end = YearMonth.parse(month).plusMonths(1).atDay(1);
    return db.queryForList(
        "SELECT to_char(date_trunc('month',date),'YYYY-MM') AS month,SUM(CASE WHEN type='INCOME'"
            + " THEN amount ELSE 0 END) AS income,SUM(CASE WHEN type='EXPENSE' THEN amount ELSE 0"
            + " END) AS expenses FROM financial_transaction WHERE user_id=? AND date>=? AND date<?"
            + " AND type<>'TRANSFER' GROUP BY date_trunc('month',date) ORDER BY"
            + " date_trunc('month',date)",
        UUID.fromString(jwt.getSubject()),
        end.minusMonths(6),
        end);
  }

  @GetMapping("/dashboard/categories")
  public List<Map<String, Object>> categories(
      @AuthenticationPrincipal Jwt jwt, @RequestParam String month) {
    var start = YearMonth.parse(month).atDay(1);
    return db.queryForList(
        "SELECT c.id,c.name,COALESCE(SUM(t.amount),0) AS spent FROM category c LEFT JOIN"
            + " financial_transaction t ON t.category_id=c.id AND t.user_id=c.user_id AND"
            + " t.type='EXPENSE' AND t.date>=? AND t.date<? WHERE c.user_id=? GROUP BY c.id,c.name"
            + " ORDER BY spent DESC",
        start,
        start.plusMonths(1),
        UUID.fromString(jwt.getSubject()));
  }
}

