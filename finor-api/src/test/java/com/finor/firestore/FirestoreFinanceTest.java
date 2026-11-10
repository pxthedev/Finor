package com.finor.firestore;

import static com.finor.firestore.FirestoreStore.*;
import static org.junit.jupiter.api.Assertions.*;

import com.finor.planning.Ledger;
import com.finor.planning.PlanningController.*;
import com.finor.transaction.FinanceController.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FirestoreFinanceTest {
  State state() {
    var s = new State(row("id", id(), "onboarded", false));
    for (String table :
        List.of(
            "accounts",
            "categories",
            "transactions",
            "requests",
            "goals",
            "schedule",
            "bills",
            "rules",
            "budgets",
            "installments")) s.tables.put(table, new LinkedHashMap<>());
    return s;
  }

  @Test
  void homeReturnsBoundedRecentHistoryAndKeepsBalanceWithMissingOrExpiredSchedule() {
    var s = state();
    s.profile.put("password_hash", "must-not-leak");
    String account = FirestoreFinance.account(s, new Account("Principal", "CHECKING", new BigDecimal("1000.00"))).get("id").toString();
    for (int i = 0; i < 15; i++) FirestoreFinance.create(s, UUID.randomUUID().toString(),
        new Transaction(UUID.fromString(account), null, null, Ledger.Type.EXPENSE, new BigDecimal("10.00"), FirestoreFinance.today().minusDays(i), "Despesa"));
    var home = FirestoreFinance.home(s, YearMonth.from(FirestoreFinance.today()));
    assertEquals(8, ((List<?>)home.get("transactions")).size());
    assertFalse(((Map<?, ?>)home.get("profile")).containsKey("password_hash"));
    assertNull(((Map<?, ?>)home.get("planning")).get("available"));
    var accounts = (List<Map<String, Object>>)home.get("accounts");
    assertEquals(new BigDecimal("850.00"), accounts.getFirst().get("balance"));
    s.put("schedule", "current", row("next_income", FirestoreFinance.today().toString(), "monthly_amount", "1000.00"));
    var expired = (Map<?, ?>)FirestoreFinance.home(s, YearMonth.from(FirestoreFinance.today())).get("planning");
    assertEquals(true, expired.get("needsUpdate"));
    assertNull(expired.get("available"));
  }

  @Test
  void installmentPaymentsDebitOnlyOnceAndKeepAvailabilityConsistent() {
    var s = state();
    String account =
        FirestoreFinance.account(s, new Account("Principal", "CHECKING", new BigDecimal("1000.00")))
            .get("id")
            .toString();
    FirestoreFinance.schedule(
        s,
        new Schedule(
            new BigDecimal("2000.00"),
            FirestoreFinance.today().plusMonths(1).plusDays(1),
            BigDecimal.ZERO,
            BigDecimal.ZERO));
    FirestoreFinance.goal(
        s, new Goal("Viagem", new BigDecimal("1000.00"), new BigDecimal("100.00"), null));
    String plan =
        FirestoreFinance.installment(
                s,
                null,
                new com.finor.planning.InstallmentPlan.Input(
                    "Notebook", new BigDecimal("100.00"), 3, 0, FirestoreFinance.today()))
            .get("id")
            .toString();
    assertEquals(new BigDecimal("700.00"), FirestoreFinance.available(s).available());
    FirestoreFinance.payInstallment(s, plan, 1, UUID.fromString(account));
    FirestoreFinance.payInstallment(s, plan, 1, UUID.fromString(account));
    assertEquals(1, s.rows("transactions").size());
    assertEquals(
        new BigDecimal("900.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
    assertEquals(new BigDecimal("700.00"), FirestoreFinance.available(s).available());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> FirestoreFinance.payInstallment(s, plan, 3, UUID.fromString(account)));
    FirestoreFinance.payInstallment(s, plan, 2, UUID.fromString(account));
    assertEquals(
        new BigDecimal("800.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
    assertEquals(new BigDecimal("700.00"), FirestoreFinance.available(s).available());
    assertEquals(2, ((Number) s.require("installments", plan).get("paid_installments")).intValue());
  }

  @Test
  void previouslyPaidInstallmentsAreNotChargedAgain() {
    var s = state();
    FirestoreFinance.account(s, new Account("Principal", "CHECKING", new BigDecimal("1000.00")));
    String plan =
        FirestoreFinance.installment(
                s,
                null,
                new com.finor.planning.InstallmentPlan.Input(
                    "Celular",
                    new BigDecimal("150.00"),
                    10,
                    4,
                    FirestoreFinance.today().minusMonths(4)))
            .get("id")
            .toString();
    assertEquals(
        new BigDecimal("1000.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
    FirestoreFinance.payInstallment(s, plan, 4, null);
    assertTrue(s.rows("transactions").isEmpty());
    FirestoreFinance.payInstallment(s, plan, 5, null);
    assertEquals(
        new BigDecimal("850.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () ->
            FirestoreFinance.installment(
                s,
                plan,
                new com.finor.planning.InstallmentPlan.Input(
                    "Celular", new BigDecimal("150.00"), 10, 4, FirestoreFinance.today())));
  }

  @Test
  void eachNewSalaryReceiptAddsIncomeButRetryDoesNotDuplicate() {
    var s = state();
    var next = FirestoreFinance.today().plusDays(20);
    String account =
        FirestoreFinance.account(s, new Account("Principal", "CHECKING", BigDecimal.ZERO))
            .get("id")
            .toString();
    FirestoreFinance.create(
        s,
        id(),
        new Transaction(
            UUID.fromString(account),
            null,
            null,
            Ledger.Type.EXPENSE,
            new BigDecimal("1000.00"),
            FirestoreFinance.today(),
            "Despesa"));
    FirestoreFinance.salary(
        s, new Salary(new BigDecimal("1000.00"), next, UUID.fromString(account)));
    assertEquals(new BigDecimal("0.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
    UUID receiptId = UUID.randomUUID();
    var receipt = new Salary(new BigDecimal("1000.00"), next, UUID.fromString(account), receiptId);
    var first = FirestoreFinance.salary(s, receipt);
    FirestoreFinance.salary(s, receipt);
    assertEquals(
        new BigDecimal("1000.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
    FirestoreFinance.salary(
        s,
        new Salary(new BigDecimal("1000.00"), next, UUID.fromString(account), UUID.randomUUID()));
    assertEquals(
        new BigDecimal("2000.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
    assertEquals(4, s.rows("transactions").size());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () ->
            FirestoreFinance.salary(
                s,
                new Salary(new BigDecimal("1500.00"), next, UUID.fromString(account), receiptId)));
    s.table("transactions").remove(first.get("id").toString());
    FirestoreFinance.salary(s, receipt);
    assertEquals(3, s.rows("transactions").size());
  }

  @Test
  void salaryAndOtherIncomeIncreaseBalanceWithoutDuplicatingSalary() {
    var s = state();
    var next = FirestoreFinance.today().plusDays(20);
    var first = FirestoreFinance.salary(s, new Salary(new BigDecimal("3000.00"), next, null));
    String account = first.get("account_id").toString();
    FirestoreFinance.salary(s, new Salary(new BigDecimal("3000.00"), next, null));
    assertEquals(1, s.rows("transactions").size());
    assertEquals(
        new BigDecimal("3000.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
    FirestoreFinance.salary(s, new Salary(new BigDecimal("3200.00"), next, null));
    assertEquals(1, s.rows("transactions").size());
    FirestoreFinance.create(
        s,
        id(),
        new Transaction(
            UUID.fromString(account),
            null,
            null,
            Ledger.Type.INCOME,
            new BigDecimal("250.00"),
            FirestoreFinance.today(),
            "Freelance"));
    FirestoreFinance.create(
        s,
        id(),
        new Transaction(
            UUID.fromString(account),
            null,
            null,
            Ledger.Type.EXPENSE,
            new BigDecimal("100.00"),
            FirestoreFinance.today(),
            "Mercado"));
    assertEquals(
        new BigDecimal("3350.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
    assertEquals(new BigDecimal("3350.00"), FirestoreFinance.available(s).available());
    assertEquals(
        new BigDecimal("3450.00"),
        FirestoreFinance.total(s, "INCOME", YearMonth.from(FirestoreFinance.today()), null));
  }

  @Test
  void salaryRequiresAccountChoiceWhenMultipleAccountsExist() {
    var s = state();
    FirestoreFinance.account(s, new Account("A", "CHECKING", BigDecimal.ZERO));
    FirestoreFinance.account(s, new Account("B", "CHECKING", BigDecimal.ZERO));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () ->
            FirestoreFinance.salary(
                s, new Salary(BigDecimal.TEN, FirestoreFinance.today().plusDays(10), null)));
    assertTrue(s.rows("transactions").isEmpty());
  }

  @Test
  void customizedDefaultsStayCustomizedAndDeletionPreservesTheLedger() {
    var s = state();
    FirestoreFinance.categoryDefaults(s);
    String category =
        s.rows("categories").stream()
            .filter(c -> "Alimentação".equals(c.get("name")))
            .findFirst()
            .orElseThrow()
            .get("id")
            .toString();
    FirestoreFinance.renameCategory(s, category, "Mercado");
    FirestoreFinance.categoryDefaults(s);
    assertEquals(10, s.rows("categories").size());
    assertTrue(s.rows("categories").stream().noneMatch(c -> "Alimentação".equals(c.get("name"))));
    String account =
        FirestoreFinance.account(s, new Account("Principal", "CHECKING", new BigDecimal("100.00")))
            .get("id")
            .toString();
    FirestoreFinance.create(
        s,
        id(),
        new Transaction(
            UUID.fromString(account),
            null,
            UUID.fromString(category),
            Ledger.Type.EXPENSE,
            new BigDecimal("20.00"),
            FirestoreFinance.today(),
            "Compra"));
    s.put(
        "budgets",
        "test",
        row("allocations", List.of(row("category_id", category, "amount", "50.00"))));
    FirestoreFinance.deleteCategory(s, category);
    FirestoreFinance.categoryDefaults(s);
    assertEquals(9, s.rows("categories").size());
    assertEquals(1, s.rows("transactions").size());
    assertNull(s.rows("transactions").getFirst().get("category_id"));
    assertTrue(((List<?>) s.rows("budgets").getFirst().get("allocations")).isEmpty());
    assertEquals(
        new BigDecimal("80.00"), money(FirestoreFinance.accounts(s).getFirst(), "balance"));
  }

  @Test
  void persistsDecimalsWithoutFloatingPoint() {
    assertEquals("99999999999999999.99", encode(new BigDecimal("99999999999999999.99")));
    assertEquals(new BigDecimal("45.90"), money(wire(row("amount", "45.90")), "amount"));
  }

  @Test
  void transferAndDeletionPreserveIdempotency() {
    var s = state();
    String
        a =
            FirestoreFinance.field(
                FirestoreFinance.account(s, new Account("A", "CHECKING", new BigDecimal("100.00"))),
                "id"),
        b =
            FirestoreFinance.field(
                FirestoreFinance.account(s, new Account("B", "CASH", BigDecimal.ZERO)), "id");
    var r =
        new Transaction(
            UUID.fromString(a),
            UUID.fromString(b),
            null,
            Ledger.Type.TRANSFER,
            new BigDecimal("40.00"),
            FirestoreFinance.today(),
            null);
    String key = id();
    var first = FirestoreFinance.create(s, key, r);
    FirestoreFinance.create(s, key, r);
    assertEquals(1, s.rows("transactions").size());
    var accounts = FirestoreFinance.accounts(s);
    assertEquals(
        new BigDecimal("100.00"),
        accounts.stream().map(v -> money(v, "balance")).reduce(BigDecimal.ZERO, BigDecimal::add));
    s.table("transactions").clear();
    assertEquals(first.get("id"), FirestoreFinance.create(s, key, r).get("id"));
    assertEquals(0, s.rows("transactions").size());
  }

  @Test
  void unknownAccountCannotBeUsed() {
    var s = state();
    var r =
        new Transaction(
            UUID.randomUUID(),
            null,
            null,
            Ledger.Type.EXPENSE,
            BigDecimal.TEN,
            FirestoreFinance.today(),
            null);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> FirestoreFinance.create(s, id(), r));
  }

  @Test
  void protectsOnlyUnpaidBillsAndCountsReservesOnce() {
    var s = state();
    FirestoreFinance.account(s, new Account("A", "CHECKING", new BigDecimal("1000.00")));
    FirestoreFinance.schedule(
        s,
        new Schedule(
            new BigDecimal("3200"),
            FirestoreFinance.today().plusDays(10),
            new BigDecimal("100"),
            new BigDecimal("50")));
    FirestoreFinance.goal(
        s, new Goal("Reserva", new BigDecimal("500"), new BigDecimal("200"), null));
    String paid = id(), pending = id();
    s.put(
        "bills",
        paid,
        row(
            "id",
            paid,
            "amount",
            "250.00",
            "paid",
            true,
            "due_date",
            FirestoreFinance.today().toString()));
    s.put(
        "bills",
        pending,
        row(
            "id",
            pending,
            "amount",
            "100.00",
            "paid",
            false,
            "due_date",
            FirestoreFinance.today().toString()));
    assertEquals(new BigDecimal("550.00"), FirestoreFinance.available(s).available());
  }

  @Test
  void monthlyProjectionUsesLastDayAndRespectsPaidOverride() {
    var s = state();
    var first = LocalDate.of(2027, 1, 31);
    var value = FirestoreFinance.rule(s, new Bill("Mensal", BigDecimal.TEN, first, false));
    var feb =
        FirestoreFinance.occurrences(s, LocalDate.of(2027, 2, 28)).stream()
            .filter(v -> "2027-02-28".equals(v.get("due_date")))
            .findFirst()
            .orElseThrow();
    feb.put("paid", true);
    s.put("bills", feb.get("id").toString(), feb);
    var again = FirestoreFinance.occurrences(s, LocalDate.of(2027, 2, 28));
    assertEquals(2, again.size());
    assertTrue(again.stream().anyMatch(v -> Boolean.TRUE.equals(v.get("paid"))));
  }
}
