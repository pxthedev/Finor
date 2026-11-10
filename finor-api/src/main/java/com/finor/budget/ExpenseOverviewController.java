package com.finor.budget;

import java.time.YearMonth;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!firestore")
@RequestMapping("/api/v1/reports")
public class ExpenseOverviewController {
  private final JdbcTemplate db;
  public ExpenseOverviewController(JdbcTemplate db) { this.db = db; }

  @GetMapping("/overview")
  public Object overview(@AuthenticationPrincipal Jwt jwt, @RequestParam String month) {
    var period = YearMonth.parse(month);
    var owner = UUID.fromString(jwt.getSubject());
    var categories = db.queryForList("SELECT id,name FROM category WHERE user_id=? ORDER BY name", owner);
    var entries = db.queryForList("SELECT type,date,category_id,SUM(amount) AS amount FROM financial_transaction WHERE user_id=? AND type IN ('INCOME','EXPENSE') GROUP BY type,date,category_id", owner);
    return ExpenseOverview.calculate(entries, categories, period);
  }
}
