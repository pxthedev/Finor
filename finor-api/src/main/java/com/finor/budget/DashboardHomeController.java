package com.finor.budget;

import com.finor.transaction.FinanceController;
import com.finor.planning.PlanningController;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!firestore")
@RequestMapping("/api/v1/dashboard")
public class DashboardHomeController {
  private final FinanceController finance;
  private final PlanningController planning;
  private final BudgetController budget;
  public DashboardHomeController(FinanceController finance, PlanningController planning, BudgetController budget) {
    this.finance = finance; this.planning = planning; this.budget = budget;
  }

  @GetMapping("/home")
  public Object home(@AuthenticationPrincipal Jwt jwt, @RequestParam String month) {
    YearMonth.parse(month);
    var schedules = planning.schedule(jwt);
    var schedule = schedules.isEmpty() ? null : schedules.getFirst();
    boolean needsUpdate = schedule != null && !LocalDate.parse(schedule.get("next_income").toString().substring(0, 10))
        .isAfter(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
    var summary = new LinkedHashMap<String, Object>();
    summary.put("schedule", schedule);
    summary.put("available", schedule == null || needsUpdate ? null : planning.available(jwt));
    summary.put("needsUpdate", needsUpdate);
    return Map.of("profile", finance.me(jwt), "accounts", finance.accounts(jwt),
        "transactions", finance.transactions(jwt, 0, null, null).stream().limit(8).toList(),
        "budgets", budget.budgets(jwt, month), "planning", summary);
  }
}
