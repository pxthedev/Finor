package com.finor.onboarding;

import com.finor.planning.PlanningController;
import com.finor.planning.PlanningController.Bill;
import com.finor.transaction.FinanceController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {
  private final JdbcTemplate db;
  private final FinanceController finance;
  private final PlanningController planning;
  private final com.finor.recurring.RecurrenceController recurrence;

  public OnboardingController(
      JdbcTemplate db,
      FinanceController finance,
      PlanningController planning,
      com.finor.recurring.RecurrenceController recurrence) {
    this.db = db;
    this.finance = finance;
    this.planning = planning;
    this.recurrence = recurrence;
  }

  public record Setup(
      @NotNull @Valid FinanceController.Account account,
      @NotNull @Valid PlanningController.Schedule schedule,
      @NotNull @Size(max = 50) List<@Valid Bill> bills,
      @Valid PlanningController.Goal goal) {}

  @Transactional
  @PostMapping
  public void setup(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Setup r) {
    var user = UUID.fromString(jwt.getSubject());
    var done =
        db.queryForObject(
            "SELECT onboarded FROM app_user WHERE id=? FOR UPDATE", Boolean.class, user);
    if (Boolean.TRUE.equals(done))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Planejamento inicial já concluído.");
    finance.account(jwt, r.account());
    planning.saveSchedule(jwt, r.schedule());
    for (var bill : r.bills()) recurrence.create(jwt, bill);
    if (r.goal() != null) planning.createGoal(jwt, r.goal());
    for (String name : List.of("Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Outros"))
      db.update(
          "INSERT INTO category(id,user_id,name) VALUES(?,?,?) ON CONFLICT(user_id,name) DO"
              + " NOTHING",
          UUID.randomUUID(),
          user,
          name);
    db.update("UPDATE app_user SET onboarded=true WHERE id=?", user);
  }
}

