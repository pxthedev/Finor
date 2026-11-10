package com.finor.firestore;

import static com.finor.firestore.FirestoreStore.*;

import com.finor.budget.BudgetController.Budget;
import com.finor.onboarding.OnboardingController.Setup;
import com.finor.planning.AvailableCalculator;
import com.finor.planning.PlanningController.*;
import com.finor.transaction.FinanceController.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("firestore")
public class FirestoreFinance {
  static final List<String> LEDGER = List.of("accounts", "categories", "transactions", "requests");
  static final List<String> PLAN =
      List.of("accounts", "transactions", "bills", "rules", "goals", "schedule", "installments");
  final FirestoreStore store;

  public FirestoreFinance(FirestoreStore store) {
    this.store = store;
  }

  static LocalDate today() {
    return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
  }

  static String str(Object value) {
    return value == null ? null : value.toString();
  }

  static String field(Map<String, Object> row, String name) {
    return str(row.get(name));
  }

  static Map<String, Object> profile(State s) {
    return row(
        "id",
        s.profile.get("id"),
        "name",
        s.profile.get("name"),
        "email",
        s.profile.get("email"),
        "onboarded",
        s.profile.get("onboarded"));
  }

  public Map<String, Object> me(String user) {
    return store.view(user, List.of(), false, FirestoreFinance::profile);
  }

  public Map<String, Object> home(String user, String month) {
    var period = YearMonth.parse(month);
    var tables = new ArrayList<>(PLAN);
    tables.addAll(List.of("categories", "budgets"));
    return store.view(user, tables, false, s -> home(s, period));
  }

  static Map<String, Object> home(State s, YearMonth period) {
    var schedule = s.table("schedule").get("current");
    boolean needsUpdate = schedule != null && !LocalDate.parse(field(schedule, "next_income")).isAfter(today());
    var recent = s.rows("transactions").stream()
        .sorted(Comparator.<Map<String, Object>, String>comparing(t -> field(t, "date")).reversed()
            .thenComparing(t -> field(t, "id")))
        .limit(8).map(FirestoreStore::wire).toList();
    return row("profile", profile(s), "accounts", accounts(s), "transactions", recent,
        "budgets", budgets(s, period), "planning", row("schedule", schedule == null ? null : wire(schedule),
            "available", schedule == null || needsUpdate ? null : available(s), "needsUpdate", needsUpdate));
  }

  static Map<String, Object> account(State s, Account r) {
    String id = id();
    var account =
        row(
            "id",
            id,
            "name",
            r.name().trim(),
            "type",
            r.type(),
            "opening_balance",
            r.openingBalance());
    s.put("accounts", id, account);
    return row("id", id);
  }

  public Map<String, Object> account(String user, Account r) {
    return store.view(user, List.of("accounts"), true, s -> account(s, r));
  }

  static Map<String, Object> category(State s, String name) {
    if (s.rows("categories").stream().anyMatch(c -> field(c, "name").equalsIgnoreCase(name.trim())))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria já cadastrada.");
    String id = id();
    s.put("categories", id, row("id", id, "name", name.trim()));
    return row("id", id);
  }

  public Map<String, Object> category(String user, String name) {
    return store.view(user, List.of("categories"), true, s -> category(s, name));
  }

  public List<Map<String, Object>> categories(String user) {
    return store.view(
        user,
        List.of("categories"),
        true,
        s -> {
          categoryDefaults(s);
          return s.rows("categories").stream()
              .sorted(Comparator.comparing(c -> field(c, "name")))
              .toList();
        });
  }

  static void categoryDefaults(State s) {
    if (Boolean.TRUE.equals(s.profile.get("categories_initialized"))) return;
    for (String name : com.finor.transaction.CategoryDefaults.NAMES) {
      if (s.rows("categories").stream().noneMatch(c -> name.equalsIgnoreCase(field(c, "name"))))
        category(s, name);
    }
    s.profile.put("categories_initialized", true);
  }

  static void renameCategory(State s, String categoryId, String name) {
    var target = s.require("categories", categoryId);
    if (s.rows("categories").stream()
        .anyMatch(
            c ->
                !categoryId.equals(field(c, "id"))
                    && name.trim().equalsIgnoreCase(field(c, "name"))))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria já cadastrada.");
    target.put("name", name.trim());
  }

  public void renameCategory(String user, String categoryId, String name) {
    store.view(
        user,
        List.of("categories"),
        true,
        s -> {
          renameCategory(s, categoryId, name);
          return null;
        });
  }

  @SuppressWarnings("unchecked")
  static void deleteCategory(State s, String categoryId) {
    s.require("categories", categoryId);
    s.table("categories").remove(categoryId);
    for (var transaction : s.rows("transactions"))
      if (categoryId.equals(field(transaction, "category_id")))
        transaction.put("category_id", null);
    for (var budget : s.rows("budgets")) {
      var allocations = (List<Map<String, Object>>) budget.get("allocations");
      budget.put(
          "allocations",
          allocations.stream().filter(a -> !categoryId.equals(field(a, "category_id"))).toList());
    }
  }

  public void deleteCategory(String user, String categoryId) {
    store.view(
        user,
        List.of("categories", "transactions", "budgets"),
        true,
        s -> {
          deleteCategory(s, categoryId);
          return null;
        });
  }

  static List<Map<String, Object>> accounts(State s) {
    var result = new ArrayList<Map<String, Object>>();
    for (var account : s.rows("accounts")) {
      var value = wire(account);
      BigDecimal balance = money(account, "opening_balance");
      for (var t : s.rows("transactions")) {
        if (LocalDate.parse(field(t, "date")).isAfter(today())) continue;
        if (Objects.equals(field(t, "account_id"), field(account, "id")))
          balance =
              field(t, "type").equals("INCOME")
                  ? balance.add(money(t, "amount"))
                  : balance.subtract(money(t, "amount"));
        if (Objects.equals(field(t, "destination_account_id"), field(account, "id")))
          balance = balance.add(money(t, "amount"));
      }
      value.put("balance", balance);
      result.add(value);
    }
    result.sort(Comparator.comparing(c -> field(c, "name")));
    return result;
  }

  public List<Map<String, Object>> accounts(String user) {
    return store.view(user, List.of("accounts", "transactions"), false, FirestoreFinance::accounts);
  }

  static void validate(State s, Transaction r) {
    new com.finor.planning.Ledger.Entry(
        r.accountId(), r.destinationAccountId(), r.type(), r.amount(), r.date());
    s.require("accounts", str(r.accountId()));
    if (r.destinationAccountId() != null) s.require("accounts", str(r.destinationAccountId()));
    if (r.categoryId() != null) s.require("categories", str(r.categoryId()));
    if (r.type() == com.finor.planning.Ledger.Type.TRANSFER && r.categoryId() != null)
      throw new IllegalArgumentException();
  }

  static String fingerprint(Transaction r) {
    return r.accountId()
        + "|"
        + r.destinationAccountId()
        + "|"
        + r.categoryId()
        + "|"
        + r.type()
        + "|"
        + r.amount().setScale(2).toPlainString()
        + "|"
        + r.date()
        + "|"
        + (r.description() == null
            ? "N"
            : "S"
                + Base64.getEncoder()
                    .encodeToString(r.description().getBytes(StandardCharsets.UTF_8)));
  }

  static Map<String, Object> transactionRow(String id, String key, Transaction r) {
    return row(
        "id",
        id,
        "account_id",
        str(r.accountId()),
        "destination_account_id",
        str(r.destinationAccountId()),
        "category_id",
        str(r.categoryId()),
        "type",
        r.type().name(),
        "amount",
        r.amount(),
        "date",
        str(r.date()),
        "description",
        r.description(),
        "idempotency_key",
        key);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> create(State s, String key, Transaction r) {
    validate(s, r);
    String fingerprint = fingerprint(r);
    var existing = s.table("requests").get(key);
    if (existing != null) {
      if (!fingerprint.equals(existing.get("fingerprint")))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Chave já utilizada com outros dados.");
      return wire((Map<String, Object>) existing.get("response"));
    }
    String id = id();
    var value = transactionRow(id, key, r);
    s.put("transactions", id, value);
    s.put("requests", key, row("id", key, "fingerprint", fingerprint, "response", value));
    return wire(value);
  }

  public Map<String, Object> create(String user, String key, Transaction r) {
    return store.view(user, LEDGER, true, s -> create(s, key, r));
  }

  public void edit(String user, String id, Transaction r) {
    store.view(
        user,
        LEDGER,
        true,
        s -> {
          var old = s.require("transactions", id);
          validate(s, r);
          s.put("transactions", id, transactionRow(id, field(old, "idempotency_key"), r));
          return null;
        });
  }

  public void delete(String user, String id) {
    store.view(
        user,
        List.of("transactions"),
        true,
        s -> {
          s.require("transactions", id);
          s.table("transactions").remove(id);
          return null;
        });
  }

  public Map<String, Object> details(String user, String id) {
    return store.view(
        user, List.of("transactions"), false, s -> wire(s.require("transactions", id)));
  }

  public List<Map<String, Object>> history(String user, int page, String category, String month) {
    YearMonth period = month == null ? null : YearMonth.parse(month);
    return store.view(
        user,
        List.of("transactions", "categories"),
        false,
        s ->
            s.rows("transactions").stream()
                .filter(t -> category == null || Objects.equals(category, field(t, "category_id")))
                .filter(
                    t ->
                        period == null
                            || YearMonth.from(LocalDate.parse(field(t, "date"))).equals(period))
                .sorted(
                    Comparator.<Map<String, Object>, String>comparing(t -> field(t, "date"))
                        .reversed()
                        .thenComparing(t -> field(t, "id")))
                .skip((long) page * 100)
                .limit(100)
                .map(
                    t -> {
                      var out = wire(t);
                      var c = s.table("categories").get(field(t, "category_id"));
                      out.put("category_name", c == null ? null : c.get("name"));
                      return out;
                    })
                .toList());
  }

  static BigDecimal total(State s, String type, YearMonth month, String category) {
    return s.rows("transactions").stream()
        .filter(
            t ->
                field(t, "type").equals(type)
                    && YearMonth.from(LocalDate.parse(field(t, "date"))).equals(month)
                    && (category == null || Objects.equals(category, field(t, "category_id"))))
        .map(t -> money(t, "amount"))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public Map<String, Object> summary(String user, String month) {
    var m = month == null ? YearMonth.from(today()) : YearMonth.parse(month);
    return store.view(
        user,
        List.of("accounts", "transactions"),
        false,
        s ->
            row(
                "income",
                total(s, "INCOME", m, null),
                "expenses",
                total(s, "EXPENSE", m, null),
                "accounts",
                accounts(s)));
  }

  static Map<String, Object> goal(State s, Goal r) {
    String id = id();
    s.put(
        "goals",
        id,
        row(
            "id",
            id,
            "name",
            r.name(),
            "target",
            r.target(),
            "reserved",
            r.reserved(),
            "deadline",
            str(r.deadline())));
    return row("id", id);
  }

  public List<Map<String, Object>> goals(String user) {
    return store.view(
        user,
        List.of("goals"),
        false,
        s -> s.rows("goals").stream().map(FirestoreStore::wire).toList());
  }

  public void deleteGoal(String user, String goalId) {
    store.view(
        user,
        List.of("goals"),
        true,
        s -> {
          s.require("goals", goalId);
          s.table("goals").remove(goalId);
          return null;
        });
  }

  static Map<String, Object> installment(
      State s, String planId, com.finor.planning.InstallmentPlan.Input r) {
    com.finor.planning.InstallmentPlan.validate(r);
    if (planId != null
        && ((Number) s.require("installments", planId).get("paid_installments")).intValue()
            != r.paidInstallments())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Use o botão de pagamento para atualizar as parcelas pagas.");
    String id = planId == null ? id() : planId;
    var value =
        row(
            "id",
            id,
            "name",
            r.name().trim(),
            "installment_amount",
            r.installmentAmount(),
            "total_installments",
            r.totalInstallments(),
            "paid_installments",
            r.paidInstallments(),
            "first_due_date",
            r.firstDueDate().toString());
    s.put("installments", id, value);
    return wire(value);
  }

  public Object installment(
      String user, String planId, com.finor.planning.InstallmentPlan.Input r) {
    return store.view(user, List.of("installments"), true, s -> installment(s, planId, r));
  }

  public Object installments(String user) {
    return store.view(
        user,
        List.of("installments"),
        false,
        s ->
            s.rows("installments").stream()
                .sorted(Comparator.comparing(p -> field(p, "name")))
                .map(FirestoreStore::wire)
                .toList());
  }

  static Object payInstallment(State s, String planId, int number, UUID selectedAccount) {
    var plan = s.require("installments", planId);
    int paid = ((Number) plan.get("paid_installments")).intValue(),
        total = ((Number) plan.get("total_installments")).intValue();
    if (number < 1 || number > total) throw new IllegalArgumentException();
    if (number <= paid) return row("alreadyPaid", true);
    if (number != paid + 1)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Pague as parcelas na ordem.");
    String account = str(selectedAccount);
    if (account == null) {
      var accounts = s.rows("accounts");
      if (accounts.size() > 1)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Escolha a conta usada no pagamento.");
      account =
          accounts.isEmpty()
              ? field(account(s, new Account("Conta principal", "CHECKING", BigDecimal.ZERO)), "id")
              : field(accounts.getFirst(), "id");
    }
    categoryDefaults(s);
    String category =
        s.rows("categories").stream()
            .filter(c -> "Contas".equals(field(c, "name")))
            .map(c -> field(c, "id"))
            .findFirst()
            .orElse(null);
    var transaction =
        new Transaction(
            UUID.fromString(account),
            null,
            category == null ? null : UUID.fromString(category),
            com.finor.planning.Ledger.Type.EXPENSE,
            money(plan, "installment_amount"),
            today(),
            field(plan, "name") + " · parcela " + number + "/" + total);
    var result =
        create(
            s,
            com.finor.planning.InstallmentPlan.paymentKey(field(s.profile, "id"), planId, number)
                .toString(),
            transaction);
    plan.put("paid_installments", number);
    return result;
  }

  public Object payInstallment(String user, String planId, int number, UUID account) {
    return store.view(
        user,
        List.of("installments", "accounts", "categories", "transactions", "requests"),
        true,
        s -> payInstallment(s, planId, number, account));
  }

  public void deleteInstallment(String user, String planId) {
    store.view(
        user,
        List.of("installments"),
        true,
        s -> {
          s.require("installments", planId);
          s.table("installments").remove(planId);
          return null;
        });
  }

  public Map<String, Object> goal(String user, Goal r) {
    return store.view(user, List.of("goals"), true, s -> goal(s, r));
  }

  public void updateGoal(String user, String id, Goal r) {
    store.view(
        user,
        List.of("goals"),
        true,
        s -> {
          s.require("goals", id);
          s.put(
              "goals",
              id,
              row(
                  "id",
                  id,
                  "name",
                  r.name(),
                  "target",
                  r.target(),
                  "reserved",
                  r.reserved(),
                  "deadline",
                  str(r.deadline())));
          return null;
        });
  }

  static void schedule(State s, Schedule r) {
    s.put(
        "schedule",
        "current",
        row(
            "id",
            "current",
            "monthly_amount",
            r.monthlyAmount(),
            "next_income",
            str(r.nextIncome()),
            "remaining_essentials",
            r.remainingEssentials(),
            "safety_margin",
            r.safetyMargin()));
  }

  static String salaryId(State s) {
    return UUID.nameUUIDFromBytes(
            ("finor:salary:" + field(s.profile, "id") + ":" + YearMonth.from(today()))
                .getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  static Map<String, Object> salary(State s, Salary r) {
    String transactionId = salaryId(s);
    var existing = s.table("transactions").get(transactionId);
    if (r.receiptId() != null) {
      var request = s.table("requests").get(r.receiptId().toString());
      existing = request == null ? null : (Map<String, Object>) request.get("response");
    }
    String accountId = str(r.accountId());
    if (accountId == null && existing != null) accountId = field(existing, "account_id");
    if (accountId == null) {
      var accounts = s.rows("accounts");
      if (accounts.size() > 1)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Escolha a conta que recebeu o salário.");
      accountId =
          accounts.isEmpty()
              ? field(account(s, new Account("Conta principal", "CHECKING", BigDecimal.ZERO)), "id")
              : field(accounts.getFirst(), "id");
    }
    categoryDefaults(s);
    String categoryId =
        existing != null
            ? field(existing, "category_id")
            : s.rows("categories").stream()
                .filter(c -> "Salário".equals(field(c, "name")))
                .map(c -> field(c, "id"))
                .findFirst()
                .orElse(null);
    var transaction =
        new Transaction(
            UUID.fromString(accountId),
            null,
            categoryId == null ? null : UUID.fromString(categoryId),
            com.finor.planning.Ledger.Type.INCOME,
            r.monthlyAmount(),
            existing == null ? today() : LocalDate.parse(field(existing, "date")),
            r.receiptId() == null ? "Salário do mês" : "Salário recebido");
    validate(s, transaction);
    if (r.receiptId() != null) {
      var response = create(s, r.receiptId().toString(), transaction);
      schedule(
          s, new Schedule(r.monthlyAmount(), r.nextIncome(), BigDecimal.ZERO, BigDecimal.ZERO));
      return response;
    }
    var value = transactionRow(transactionId, transactionId, transaction);
    s.put("transactions", transactionId, value);
    schedule(s, new Schedule(r.monthlyAmount(), r.nextIncome(), BigDecimal.ZERO, BigDecimal.ZERO));
    return wire(value);
  }

  public Object salary(String user, Salary r) {
    return store.view(
        user,
        List.of("accounts", "categories", "transactions", "schedule", "requests"),
        true,
        s -> salary(s, r));
  }

  public Object salary(String user) {
    return store.view(
        user,
        List.of("transactions"),
        false,
        s -> {
          var transaction = s.table("transactions").get(salaryId(s));
          return transaction == null ? List.of() : List.of(wire(transaction));
        });
  }

  public void schedule(String user, Schedule r) {
    store.view(
        user,
        List.of("schedule"),
        true,
        s -> {
          schedule(s, r);
          return null;
        });
  }

  public List<Map<String, Object>> schedule(String user) {
    return store.view(
        user,
        List.of("schedule"),
        false,
        s -> s.rows("schedule").stream().map(FirestoreStore::wire).toList());
  }

  static Map<String, Object> rule(State s, Bill r) {
    String id = id();
    s.put(
        "rules",
        id,
        row(
            "id",
            id,
            "name",
            r.name(),
            "amount",
            r.amount(),
            "first_date",
            str(r.dueDate()),
            "active",
            true));
    return row("id", id);
  }

  static List<Map<String, Object>> occurrences(State s, LocalDate until) {
    var result = new LinkedHashMap<String, Map<String, Object>>();
    for (var b : s.rows("bills")) result.put(field(b, "id"), b);
    for (var rule : s.rows("rules")) {
      if (!Boolean.TRUE.equals(rule.get("active"))) continue;
      var first = LocalDate.parse(field(rule, "first_date"));
      for (var month = YearMonth.from(first);
          !month.isAfter(YearMonth.from(until));
          month = month.plusMonths(1)) {
        var date = month.atDay(Math.min(first.getDayOfMonth(), month.lengthOfMonth()));
        if (date.isBefore(first) || date.isAfter(until)) continue;
        String id =
            UUID.nameUUIDFromBytes(
                    (field(rule, "id") + "|" + date).getBytes(StandardCharsets.UTF_8))
                .toString();
        result.putIfAbsent(
            id,
            row(
                "id",
                id,
                "name",
                rule.get("name"),
                "amount",
                rule.get("amount"),
                "due_date",
                str(date),
                "paid",
                false,
                "rule_id",
                rule.get("id")));
      }
    }
    return new ArrayList<>(result.values());
  }

  public List<Map<String, Object>> bills(String user) {
    return store.view(
        user,
        List.of("bills", "rules"),
        false,
        s ->
            occurrences(s, today().plusMonths(12)).stream()
                .sorted(Comparator.comparing(b -> field(b, "due_date")))
                .map(FirestoreStore::wire)
                .toList());
  }

  public Map<String, Object> bill(String user, Bill r, boolean recurring) {
    return store.view(
        user,
        List.of("bills", "rules"),
        true,
        s -> {
          if (recurring) return rule(s, r);
          String id = id();
          s.put(
              "bills",
              id,
              row(
                  "id",
                  id,
                  "name",
                  r.name(),
                  "amount",
                  r.amount(),
                  "due_date",
                  str(r.dueDate()),
                  "paid",
                  r.paid(),
                  "rule_id",
                  null));
          return row("id", id);
        });
  }

  public void updateBill(String user, String id, Bill r) {
    store.view(
        user,
        List.of("bills", "rules"),
        true,
        s -> {
          var existing =
              occurrences(s, today().plusMonths(12)).stream()
                  .filter(b -> field(b, "id").equals(id))
                  .findFirst()
                  .orElseThrow(FirestoreStore::missing);
          s.put(
              "bills",
              id,
              row(
                  "id",
                  id,
                  "name",
                  r.name(),
                  "amount",
                  r.amount(),
                  "due_date",
                  str(r.dueDate()),
                  "paid",
                  r.paid(),
                  "rule_id",
                  existing.get("rule_id")));
          return null;
        });
  }

  public void stopRule(String user, String id) {
    store.view(
        user,
        List.of("rules", "bills"),
        true,
        s -> {
          var rule = s.require("rules", id);
          for (var b : occurrences(s, today()))
            if (Objects.equals(field(b, "rule_id"), id)
                && !LocalDate.parse(field(b, "due_date")).isAfter(today()))
              s.put("bills", field(b, "id"), b);
          rule.put("active", false);
          s.table("bills")
              .entrySet()
              .removeIf(
                  e ->
                      Objects.equals(field(e.getValue(), "rule_id"), id)
                          && LocalDate.parse(field(e.getValue(), "due_date")).isAfter(today())
                          && !Boolean.TRUE.equals(e.getValue().get("paid")));
          return null;
        });
  }

  static AvailableCalculator.Result available(State s) {
    var schedule = s.table("schedule").get("current");
    if (schedule == null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Configure seu próximo recebimento e os gastos essenciais previstos.");
    var next = LocalDate.parse(field(schedule, "next_income"));
    if (!next.isAfter(today()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Atualize a data do próximo recebimento.");
    var balance =
        accounts(s).stream().map(a -> money(a, "balance")).reduce(BigDecimal.ZERO, BigDecimal::add);
    var obligations = new ArrayList<AvailableCalculator.Obligation>();
    for (var plan : s.rows("installments")) {
      int paid = ((Number) plan.get("paid_installments")).intValue(),
          total = ((Number) plan.get("total_installments")).intValue();
      var first = LocalDate.parse(field(plan, "first_due_date"));
      for (int index = paid; index < total && first.plusMonths(index).isBefore(next); index++)
        obligations.add(
            new AvailableCalculator.Obligation(
                money(plan, "installment_amount"), false, first.plusMonths(index)));
    }
    for (var b : occurrences(s, next))
      obligations.add(
          new AvailableCalculator.Obligation(
              money(b, "amount"),
              Boolean.TRUE.equals(b.get("paid")),
              LocalDate.parse(field(b, "due_date"))));
    for (var t : s.rows("transactions"))
      if (field(t, "type").equals("EXPENSE") && LocalDate.parse(field(t, "date")).isAfter(today()))
        obligations.add(
            new AvailableCalculator.Obligation(
                money(t, "amount"), false, LocalDate.parse(field(t, "date"))));
    var reserve =
        s.rows("goals").stream()
            .map(g -> money(g, "reserved"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return AvailableCalculator.calculate(
        balance,
        obligations,
        money(schedule, "remaining_essentials"),
        reserve,
        money(schedule, "safety_margin"),
        today(),
        next);
  }

  public AvailableCalculator.Result available(String user) {
    return store.view(user, PLAN, false, FirestoreFinance::available);
  }

  public Map<String, Object> simulate(String user, BigDecimal amount) {
    var remaining = available(user).available().subtract(amount);
    return row(
        "remaining",
        remaining,
        "fitsRegisteredBudget",
        remaining.signum() >= 0,
        "notice",
        "Simulação limitada às contas e previsões registradas; não garante segurança financeira.");
  }

  public void onboarding(String user, Setup r) {
    store.view(
        user,
        List.of("accounts", "categories", "schedule", "goals", "bills", "rules"),
        true,
        s -> {
          if (Boolean.TRUE.equals(s.profile.get("onboarded")))
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Planejamento inicial já concluído.");
          account(s, r.account());
          schedule(s, r.schedule());
          for (var b : r.bills()) rule(s, b);
          if (r.goal() != null) goal(s, r.goal());
          for (String name :
              List.of("Alimentação", "Transporte", "Moradia", "Saúde", "Lazer", "Outros"))
            if (s.rows("categories").stream()
                .noneMatch(c -> field(c, "name").equalsIgnoreCase(name))) category(s, name);
          s.profile.put("onboarded", true);
          return null;
        });
  }

  public void budget(String user, Budget r) {
    YearMonth.parse(r.month());
    store.view(
        user,
        List.of("budgets", "categories"),
        true,
        s -> {
          var seen = new HashSet<UUID>();
          var values = new ArrayList<Map<String, Object>>();
          for (var a : r.allocations()) {
            if (!seen.add(a.categoryId())) throw new IllegalArgumentException();
            s.require("categories", str(a.categoryId()));
            values.add(row("category_id", str(a.categoryId()), "amount", a.amount()));
          }
          s.put(
              "budgets",
              r.month(),
              row("id", r.month(), "month", r.month(), "allocations", values));
          return null;
        });
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> budgets(String user, String month) {
    var period = YearMonth.parse(month);
    return store.view(
        user,
        List.of("budgets", "transactions", "categories"),
        false,
        s -> budgets(s, period));
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> budgets(State s, YearMonth period) {
          String month = period.toString();
          var budget = s.table("budgets").get(month);
          if (budget == null) return List.of();
          var result = new ArrayList<Map<String, Object>>();
          for (var a : (List<Map<String, Object>>) budget.get("allocations")) {
            String c = field(a, "category_id");
            var category = s.require("categories", c);
            result.add(
                row(
                    "category_id",
                    c,
                    "name",
                    category.get("name"),
                    "amount",
                    money(a, "amount"),
                    "spent",
                    total(s, "EXPENSE", period, c)));
          }
          return result;
  }

  public List<Map<String, Object>> categoryReport(String user, String month) {
    var m = YearMonth.parse(month);
    return store.view(
        user,
        List.of("categories", "transactions"),
        false,
        s ->
            s.rows("categories").stream()
                .map(
                    c ->
                        row(
                            "id",
                            c.get("id"),
                            "name",
                            c.get("name"),
                            "spent",
                            total(s, "EXPENSE", m, field(c, "id"))))
                .toList());
  }

  public Map<String, Object> expenseOverview(String user, String month) {
    var period = YearMonth.parse(month);
    return store.view(user, List.of("categories", "transactions"), false,
        s -> com.finor.budget.ExpenseOverview.calculate(s.rows("transactions"), s.rows("categories"), period));
  }

  public List<Map<String, Object>> evolution(String user, String month) {
    var end = YearMonth.parse(month);
    return store.view(
        user,
        List.of("transactions"),
        false,
        s -> {
          var result = new ArrayList<Map<String, Object>>();
          for (var m = end.minusMonths(5); !m.isAfter(end); m = m.plusMonths(1))
            result.add(
                row(
                    "month",
                    m.toString(),
                    "income",
                    total(s, "INCOME", m, null),
                    "expenses",
                    total(s, "EXPENSE", m, null)));
          return result;
        });
  }

  public Map<String, Object> suggestions(String user) {
    return store.view(
        user,
        PLAN,
        false,
        s -> {
          var result = available(s);
          var schedule = s.require("schedule", "current");
          var income = money(schedule, "monthly_amount");
          return row(
              "income",
              income,
              "obligations",
              result.pending(),
              "essentials",
              result.essentials(),
              "margin",
              result.margin(),
              "unallocated",
              income
                  .subtract(result.pending())
                  .subtract(result.essentials())
                  .subtract(result.margin()),
              "notice",
              "Revise as obrigações e escolha quanto destinar às metas. Esta sugestão não altera"
                  + " seus registros.");
        });
  }
}
