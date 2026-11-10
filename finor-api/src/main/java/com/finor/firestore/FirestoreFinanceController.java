package com.finor.firestore;

import com.finor.budget.BudgetController.Budget;
import com.finor.onboarding.OnboardingController.Setup;
import com.finor.planning.PlanningController.*;
import com.finor.transaction.FinanceController.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("firestore")
@RequestMapping("/api/v1")
@org.springframework.validation.annotation.Validated
public class FirestoreFinanceController {
  private final FirestoreFinance f;

  @GetMapping("/budgets")
  public Object budgets(@AuthenticationPrincipal Jwt j, @RequestParam String month) {
    return f.budgets(j.getSubject(), month);
  }

  @PostMapping("/budgets")
  public void budget(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Budget r) {
    f.budget(j.getSubject(), r);
  }

  public FirestoreFinanceController(FirestoreFinance f) {
    this.f = f;
  }

  @GetMapping("/users/me")
  public Object me(@AuthenticationPrincipal Jwt j) {
    return f.me(j.getSubject());
  }

  @GetMapping("/accounts")
  public Object accounts(@AuthenticationPrincipal Jwt j) {
    return f.accounts(j.getSubject());
  }

  @PostMapping("/accounts")
  public Object account(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Account r) {
    return f.account(j.getSubject(), r);
  }

  @GetMapping("/categories")
  public Object categories(@AuthenticationPrincipal Jwt j) {
    return f.categories(j.getSubject());
  }

  @PostMapping("/categories")
  public Object category(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Category r) {
    return f.category(j.getSubject(), r.name());
  }

  @PatchMapping("/categories/{id}")
  public void renameCategory(
      @AuthenticationPrincipal Jwt j, @PathVariable UUID id, @Valid @RequestBody Category r) {
    f.renameCategory(j.getSubject(), id.toString(), r.name());
  }

  @DeleteMapping("/categories/{id}")
  public void deleteCategory(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    f.deleteCategory(j.getSubject(), id.toString());
  }

  @GetMapping("/transactions")
  public Object history(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String month) {
    return f.history(
        j.getSubject(), page, categoryId == null ? null : categoryId.toString(), month);
  }

  @GetMapping("/transactions/{id}")
  public Object details(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    return f.details(j.getSubject(), id.toString());
  }

  @PostMapping("/transactions")
  public Object create(
      @AuthenticationPrincipal Jwt j,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody Transaction r) {
    return f.create(j.getSubject(), key.toString(), r);
  }

  @PatchMapping("/transactions/{id}")
  public void edit(
      @AuthenticationPrincipal Jwt j, @PathVariable UUID id, @Valid @RequestBody Transaction r) {
    f.edit(j.getSubject(), id.toString(), r);
  }

  @DeleteMapping("/transactions/{id}")
  public void delete(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    f.delete(j.getSubject(), id.toString());
  }

  @GetMapping("/dashboard/summary")
  public Object summary(
      @AuthenticationPrincipal Jwt j, @RequestParam(required = false) String month) {
    return f.summary(j.getSubject(), month);
  }

  @GetMapping("/dashboard/home")
  public Object home(@AuthenticationPrincipal Jwt j, @RequestParam String month) {
    return f.home(j.getSubject(), month);
  }

  @GetMapping("/dashboard/categories")
  public Object categoryReport(@AuthenticationPrincipal Jwt j, @RequestParam String month) {
    return f.categoryReport(j.getSubject(), month);
  }

  @GetMapping("/reports/evolution")
  public Object evolution(@AuthenticationPrincipal Jwt j, @RequestParam String month) {
    return f.evolution(j.getSubject(), month);
  }

  @GetMapping("/reports/overview")
  public Object expenseOverview(@AuthenticationPrincipal Jwt j, @RequestParam String month) {
    return f.expenseOverview(j.getSubject(), month);
  }

  @GetMapping("/goals")
  public Object goals(@AuthenticationPrincipal Jwt j) {
    return f.goals(j.getSubject());
  }

  @PostMapping("/goals")
  public Object goal(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Goal r) {
    return f.goal(j.getSubject(), r);
  }

  @PatchMapping("/goals/{id}")
  public void goal(
      @AuthenticationPrincipal Jwt j, @PathVariable UUID id, @Valid @RequestBody Goal r) {
    f.updateGoal(j.getSubject(), id.toString(), r);
  }

  @DeleteMapping("/goals/{id}")
  public void deleteGoal(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    f.deleteGoal(j.getSubject(), id.toString());
  }

  @GetMapping("/installments")
  public Object installments(@AuthenticationPrincipal Jwt j) {
    return f.installments(j.getSubject());
  }

  @PostMapping("/installments")
  public Object installment(
      @AuthenticationPrincipal Jwt j,
      @Valid @RequestBody com.finor.planning.InstallmentPlan.Input r) {
    return f.installment(j.getSubject(), null, r);
  }

  @PatchMapping("/installments/{id}")
  public Object installment(
      @AuthenticationPrincipal Jwt j,
      @PathVariable UUID id,
      @Valid @RequestBody com.finor.planning.InstallmentPlan.Input r) {
    return f.installment(j.getSubject(), id.toString(), r);
  }

  @PostMapping("/installments/{id}/payments/{number}")
  public Object payInstallment(
      @AuthenticationPrincipal Jwt j,
      @PathVariable UUID id,
      @PathVariable int number,
      @RequestBody com.finor.planning.InstallmentPlan.Payment r) {
    return f.payInstallment(j.getSubject(), id.toString(), number, r.accountId());
  }

  @DeleteMapping("/installments/{id}")
  public void deleteInstallment(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    f.deleteInstallment(j.getSubject(), id.toString());
  }

  @GetMapping("/planning/salary")
  public Object salary(@AuthenticationPrincipal Jwt j) {
    return f.salary(j.getSubject());
  }

  @PostMapping("/planning/salary")
  public Object salary(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Salary r) {
    return f.salary(j.getSubject(), r);
  }

  @GetMapping("/planning/schedule")
  public Object schedule(@AuthenticationPrincipal Jwt j) {
    return f.schedule(j.getSubject());
  }

  @PostMapping("/planning/schedule")
  public void schedule(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Schedule r) {
    f.schedule(j.getSubject(), r);
  }

  @GetMapping("/planning/available")
  public Object available(@AuthenticationPrincipal Jwt j) {
    return f.available(j.getSubject());
  }

  @PostMapping("/planning/simulate")
  public Object simulate(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Purchase r) {
    return f.simulate(j.getSubject(), r.amount());
  }

  @PostMapping("/planning/suggestions")
  public Object suggestions(@AuthenticationPrincipal Jwt j) {
    return f.suggestions(j.getSubject());
  }

  @GetMapping("/recurring")
  public Object bills(@AuthenticationPrincipal Jwt j) {
    return f.bills(j.getSubject());
  }

  @PostMapping("/recurring")
  public Object bill(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Bill r) {
    return f.bill(j.getSubject(), r, false);
  }

  @PatchMapping("/recurring/{id}")
  public void bill(
      @AuthenticationPrincipal Jwt j, @PathVariable UUID id, @Valid @RequestBody Bill r) {
    f.updateBill(j.getSubject(), id.toString(), r);
  }

  @PostMapping("/recurring/rules")
  public Object rule(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Bill r) {
    return f.bill(j.getSubject(), r, true);
  }

  @DeleteMapping("/recurring/rules/{id}")
  public void stop(@AuthenticationPrincipal Jwt j, @PathVariable UUID id) {
    f.stopRule(j.getSubject(), id.toString());
  }

  @PostMapping("/onboarding")
  public void onboarding(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Setup r) {
    f.onboarding(j.getSubject(), r);
  }
}
