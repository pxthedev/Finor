package com.finor.planning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.context.annotation.Profile("!firestore")
@RestController
@RequestMapping("/api/v1")
public class PlanningController {
  private final JdbcTemplate db;
  private final com.finor.transaction.FinanceController finance;
  private final com.fasterxml.jackson.databind.ObjectMapper json;

  public PlanningController(
      JdbcTemplate db,
      com.finor.transaction.FinanceController finance,
      com.fasterxml.jackson.databind.ObjectMapper json) {
    this.db = db;
    this.finance = finance;
    this.json = json;
  }

  UUID user(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  public record Goal(
      @NotBlank @Size(max = 120) String name,
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal target,
      @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal reserved,
      LocalDate deadline) {}

  public record Schedule(
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal monthlyAmount,
      @NotNull @Future LocalDate nextIncome,
      @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal remainingEssentials,
      @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal safetyMargin) {}

  public record Salary(
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal monthlyAmount,
      @NotNull @Future LocalDate nextIncome,
      UUID accountId,
      UUID receiptId) {
    public Salary(BigDecimal monthlyAmount, LocalDate nextIncome, UUID accountId) {
      this(monthlyAmount, nextIncome, accountId, null);
    }
  }

  static UUID salaryId(UUID owner) {
    return UUID.nameUUIDFromBytes(
        ("finor:salary:"
                + owner
                + ":"
                + java.time.YearMonth.now(java.time.ZoneId.of("America/Sao_Paulo")))
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @GetMapping("/planning/salary")
  public List<Map<String, Object>> salary(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "SELECT * FROM financial_transaction WHERE id=? AND user_id=?",
        salaryId(user(jwt)),
        user(jwt));
  }

  @PostMapping("/planning/salary")
  @org.springframework.transaction.annotation.Transactional
  public Object saveSalary(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Salary r) {
    UUID owner = user(jwt), transactionId = salaryId(owner);
    db.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", UUID.class, owner);
    if (r.receiptId() != null) return saveSalaryReceipt(jwt, r);
    var existing = salary(jwt);
    UUID accountId = r.accountId();
    if (accountId == null && !existing.isEmpty())
      accountId = (UUID) existing.getFirst().get("account_id");
    if (accountId == null) {
      var accounts = finance.accounts(jwt);
      if (accounts.size() > 1)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Escolha a conta que recebeu o salário.");
      accountId =
          accounts.isEmpty()
              ? (UUID)
                  finance
                      .account(
                          jwt,
                          new com.finor.transaction.FinanceController.Account(
                              "Conta principal", "CHECKING", BigDecimal.ZERO))
                      .get("id")
              : (UUID) accounts.getFirst().get("id");
    }
    if (db.queryForObject(
            "SELECT COUNT(*) FROM account WHERE id=? AND user_id=?",
            Integer.class,
            accountId,
            owner)
        != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada.");
    finance.categories(jwt);
    var category =
        db.queryForList("SELECT id FROM category WHERE user_id=? AND name='Salário'", owner);
    Object categoryId =
        existing.isEmpty()
            ? (category.isEmpty() ? null : category.getFirst().get("id"))
            : existing.getFirst().get("category_id");
    db.update(
        "INSERT INTO"
            + " financial_transaction(id,user_id,account_id,type,amount,date,description,idempotency_key,category_id)"
            + " VALUES(?,?,?,'INCOME',?,CURRENT_DATE,?,?,?) ON CONFLICT(id) DO UPDATE SET"
            + " account_id=EXCLUDED.account_id,amount=EXCLUDED.amount,type='INCOME',destination_account_id=NULL",
        transactionId,
        owner,
        accountId,
        r.monthlyAmount(),
        "Salário do mês",
        transactionId,
        categoryId);
    saveSchedule(
        jwt, new Schedule(r.monthlyAmount(), r.nextIncome(), BigDecimal.ZERO, BigDecimal.ZERO));
    return salary(jwt).getFirst();
  }

  private Object saveSalaryReceipt(Jwt jwt, Salary r) {
    UUID owner = user(jwt);
    Map<String, Object> previous = null;
    var requests =
        db.queryForList(
            "SELECT response_json FROM transaction_request WHERE user_id=? AND idempotency_key=?",
            owner,
            r.receiptId());
    if (!requests.isEmpty() && requests.getFirst().get("response_json") != null) {
      try {
        previous =
            json.readValue(
                requests.getFirst().get("response_json").toString(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException("Falha ao recuperar recebimento.");
      }
    }
    UUID account = r.accountId();
    if (account == null && previous != null)
      account = UUID.fromString(previous.get("account_id").toString());
    if (account == null) {
      var accounts = finance.accounts(jwt);
      if (accounts.size() > 1)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Escolha a conta que recebeu o salário.");
      account =
          accounts.isEmpty()
              ? (UUID)
                  finance
                      .account(
                          jwt,
                          new com.finor.transaction.FinanceController.Account(
                              "Conta principal", "CHECKING", BigDecimal.ZERO))
                      .get("id")
              : (UUID) accounts.getFirst().get("id");
    }
    var categories = finance.categories(jwt);
    UUID category =
        previous == null
            ? categories.stream()
                .filter(c -> "Salário".equals(c.get("name")))
                .map(c -> (UUID) c.get("id"))
                .findFirst()
                .orElse(null)
            : previous.get("category_id") == null
                ? null
                : UUID.fromString(previous.get("category_id").toString());
    LocalDate date =
        previous == null
            ? LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))
            : LocalDate.parse(previous.get("date").toString());
    var response =
        finance.create(
            jwt,
            r.receiptId(),
            new com.finor.transaction.FinanceController.Transaction(
                account,
                null,
                category,
                com.finor.planning.Ledger.Type.INCOME,
                r.monthlyAmount(),
                date,
                "Salário recebido"));
    saveSchedule(
        jwt, new Schedule(r.monthlyAmount(), r.nextIncome(), BigDecimal.ZERO, BigDecimal.ZERO));
    return response;
  }

  public record Bill(
      @NotBlank @Size(max = 120) String name,
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
      @NotNull LocalDate dueDate,
      boolean paid) {}

  public record Purchase(
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

  @GetMapping("/goals")
  public List<Map<String, Object>> goals(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "SELECT id,name,target,reserved,deadline FROM financial_goal WHERE user_id=? ORDER BY name",
        user(jwt));
  }

  @PostMapping("/goals")
  public Map<String, Object> createGoal(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Goal r) {
    var id = UUID.randomUUID();
    db.update(
        "INSERT INTO financial_goal(id,user_id,name,target,reserved,deadline) VALUES(?,?,?,?,?,?)",
        id,
        user(jwt),
        r.name(),
        r.target(),
        r.reserved(),
        r.deadline());
    return Map.of("id", id);
  }

  @PatchMapping("/goals/{id}")
  public void updateGoal(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Goal r) {
    if (db.update(
            "UPDATE financial_goal SET name=?,target=?,reserved=?,deadline=? WHERE id=? AND"
                + " user_id=?",
            r.name(),
            r.target(),
            r.reserved(),
            r.deadline(),
            id,
            user(jwt))
        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @DeleteMapping("/goals/{id}")
  public void deleteGoal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    if (db.update("DELETE FROM financial_goal WHERE id=? AND user_id=?", id, user(jwt)) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @GetMapping("/installments")
  public List<Map<String, Object>> installments(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "SELECT * FROM installment_plan WHERE user_id=? ORDER BY name", user(jwt));
  }

  @PostMapping("/installments")
  public Object installment(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InstallmentPlan.Input r) {
    InstallmentPlan.validate(r);
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " installment_plan(id,user_id,name,installment_amount,total_installments,paid_installments,first_due_date)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        user(jwt),
        r.name().trim(),
        r.installmentAmount(),
        r.totalInstallments(),
        r.paidInstallments(),
        r.firstDueDate());
    return Map.of("id", id);
  }

  @PatchMapping("/installments/{id}")
  @org.springframework.transaction.annotation.Transactional
  public void installment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody InstallmentPlan.Input r) {
    InstallmentPlan.validate(r);
    var rows =
        db.queryForList(
            "SELECT * FROM installment_plan WHERE id=? AND user_id=? FOR UPDATE", id, user(jwt));
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    if (((Number) rows.getFirst().get("paid_installments")).intValue() != r.paidInstallments())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Use o botão de pagamento para atualizar as parcelas pagas.");
    db.update(
        "UPDATE installment_plan SET"
            + " name=?,installment_amount=?,total_installments=?,first_due_date=? WHERE id=? AND"
            + " user_id=?",
        r.name().trim(),
        r.installmentAmount(),
        r.totalInstallments(),
        r.firstDueDate(),
        id,
        user(jwt));
  }

  @DeleteMapping("/installments/{id}")
  public void deleteInstallment(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    if (db.update("DELETE FROM installment_plan WHERE id=? AND user_id=?", id, user(jwt)) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @PostMapping("/installments/{id}/payments/{number}")
  @org.springframework.transaction.annotation.Transactional
  public Object payInstallment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable int number,
      @RequestBody InstallmentPlan.Payment r) {
    UUID owner = user(jwt);
    var rows =
        db.queryForList(
            "SELECT * FROM installment_plan WHERE id=? AND user_id=? FOR UPDATE", id, owner);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var plan = rows.getFirst();
    int paid = ((Number) plan.get("paid_installments")).intValue(),
        total = ((Number) plan.get("total_installments")).intValue();
    if (number < 1 || number > total) throw new IllegalArgumentException();
    if (number <= paid) return Map.of("alreadyPaid", true);
    if (number != paid + 1)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Pague as parcelas na ordem.");
    UUID account = r.accountId();
    if (account == null) {
      var accounts = finance.accounts(jwt);
      if (accounts.size() > 1)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Escolha a conta usada no pagamento.");
      account =
          accounts.isEmpty()
              ? (UUID)
                  finance
                      .account(
                          jwt,
                          new com.finor.transaction.FinanceController.Account(
                              "Conta principal", "CHECKING", BigDecimal.ZERO))
                      .get("id")
              : (UUID) accounts.getFirst().get("id");
    }
    UUID category =
        finance.categories(jwt).stream()
            .filter(c -> "Contas".equals(c.get("name")))
            .map(c -> (UUID) c.get("id"))
            .findFirst()
            .orElse(null);
    var transaction =
        new com.finor.transaction.FinanceController.Transaction(
            account,
            null,
            category,
            com.finor.planning.Ledger.Type.EXPENSE,
            (BigDecimal) plan.get("installment_amount"),
            LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")),
            plan.get("name") + " · parcela " + number + "/" + total);
    var result =
        finance.create(
            jwt, InstallmentPlan.paymentKey(owner.toString(), id.toString(), number), transaction);
    db.update(
        "UPDATE installment_plan SET paid_installments=? WHERE id=? AND user_id=?",
        number,
        id,
        owner);
    return result;
  }

  @GetMapping("/planning/schedule")
  public List<Map<String, Object>> schedule(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "SELECT monthly_amount,next_income,remaining_essentials,safety_margin FROM income_schedule"
            + " WHERE user_id=?",
        user(jwt));
  }

  @PostMapping("/planning/schedule")
  public void saveSchedule(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Schedule r) {
    db.update(
        "INSERT INTO"
            + " income_schedule(user_id,monthly_amount,next_income,remaining_essentials,safety_margin)"
            + " VALUES(?,?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET"
            + " monthly_amount=EXCLUDED.monthly_amount,next_income=EXCLUDED.next_income,remaining_essentials=EXCLUDED.remaining_essentials,safety_margin=EXCLUDED.safety_margin",
        user(jwt),
        r.monthlyAmount(),
        r.nextIncome(),
        r.remainingEssentials(),
        r.safetyMargin());
  }

  @GetMapping("/recurring")
  public List<Map<String, Object>> bills(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "SELECT id,name,amount,due_date,paid,rule_id FROM recurring_entry WHERE user_id=? ORDER BY"
            + " due_date",
        user(jwt));
  }

  @PostMapping("/recurring")
  public Map<String, Object> bill(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Bill r) {
    var id = UUID.randomUUID();
    db.update(
        "INSERT INTO recurring_entry(id,user_id,name,amount,due_date,paid) VALUES(?,?,?,?,?,?)",
        id,
        user(jwt),
        r.name(),
        r.amount(),
        r.dueDate(),
        r.paid());
    return Map.of("id", id);
  }

  @PatchMapping("/recurring/{id}")
  public void updateBill(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Bill r) {
    if (db.update(
            "UPDATE recurring_entry SET name=?,amount=?,due_date=?,paid=? WHERE id=? AND user_id=?",
            r.name(),
            r.amount(),
            r.dueDate(),
            r.paid(),
            id,
            user(jwt))
        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @GetMapping("/planning/available")
  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public AvailableCalculator.Result available(@AuthenticationPrincipal Jwt jwt) {
    var schedules = schedule(jwt);
    if (schedules.isEmpty())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Configure seu próximo recebimento e os gastos essenciais previstos.");
    var s = schedules.getFirst();
    var nextIncome = ((java.sql.Date) s.get("next_income")).toLocalDate();
    if (!nextIncome.isAfter(LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Atualize a data do próximo recebimento no planejamento.");
    var balance =
        finance.accounts(jwt).stream()
            .map(a -> (BigDecimal) a.get("balance"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var obligations =
        new java.util.ArrayList<>(
            bills(jwt).stream()
                .map(
                    b ->
                        new AvailableCalculator.Obligation(
                            (BigDecimal) b.get("amount"),
                            (Boolean) b.get("paid"),
                            ((java.sql.Date) b.get("due_date")).toLocalDate()))
                .toList());
    for (var planned :
        db.queryForList(
            "SELECT amount,date FROM financial_transaction WHERE user_id=? AND type='EXPENSE' AND"
                + " date>CURRENT_DATE AND date<?",
            user(jwt),
            nextIncome)) {
      obligations.add(
          new AvailableCalculator.Obligation(
              (BigDecimal) planned.get("amount"),
              false,
              ((java.sql.Date) planned.get("date")).toLocalDate()));
    }
    var reserves =
        goals(jwt).stream()
            .map(g -> (BigDecimal) g.get("reserved"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    for (var plan : installments(jwt)) {
      int paid = ((Number) plan.get("paid_installments")).intValue(),
          total = ((Number) plan.get("total_installments")).intValue();
      var first = ((java.sql.Date) plan.get("first_due_date")).toLocalDate();
      for (int index = paid; index < total && first.plusMonths(index).isBefore(nextIncome); index++)
        obligations.add(
            new AvailableCalculator.Obligation(
                (BigDecimal) plan.get("installment_amount"), false, first.plusMonths(index)));
    }
    return AvailableCalculator.calculate(
        balance,
        obligations,
        (BigDecimal) s.get("remaining_essentials"),
        reserves,
        (BigDecimal) s.get("safety_margin"),
        LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")),
        nextIncome);
  }

  @PostMapping("/planning/simulate")
  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public Map<String, Object> simulate(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Purchase r) {
    var available = available(jwt);
    var remaining = available.available().subtract(r.amount());
    return Map.of(
        "remaining",
        remaining,
        "fitsRegisteredBudget",
        remaining.signum() >= 0,
        "notice",
        "Simulação limitada às contas e previsões registradas; não garante segurança financeira.");
  }
}
