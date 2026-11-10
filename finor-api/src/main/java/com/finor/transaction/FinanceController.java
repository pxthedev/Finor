package com.finor.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.validation.annotation.Validated
@org.springframework.context.annotation.Profile("!firestore")
@RestController
@RequestMapping("/api/v1")
public class FinanceController {
  private final JdbcTemplate db;
  private final com.fasterxml.jackson.databind.ObjectMapper json;

  public FinanceController(JdbcTemplate db, com.fasterxml.jackson.databind.ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private UUID user(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  public record Account(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Pattern(regexp = "CHECKING|SAVINGS|CASH") String type,
      @NotNull @Digits(integer = 17, fraction = 2) BigDecimal openingBalance) {}

  public record Category(@NotBlank @Size(max = 80) String name) {}

  public record Transaction(
      @NotNull UUID accountId,
      UUID destinationAccountId,
      UUID categoryId,
      @NotNull com.finor.planning.Ledger.Type type,
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
      @NotNull LocalDate date,
      @Size(max = 300) String description) {}

  @GetMapping("/users/me")
  public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForMap("SELECT id,name,email,onboarded FROM app_user WHERE id=?", user(jwt));
  }

  @GetMapping("/accounts")
  public List<Map<String, Object>> accounts(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "SELECT a.*, a.opening_balance+COALESCE((SELECT SUM(CASE WHEN t.account_id=a.id THEN CASE"
            + " WHEN t.type='INCOME' THEN t.amount ELSE -t.amount END ELSE t.amount END) FROM"
            + " financial_transaction t WHERE t.user_id=a.user_id AND (t.account_id=a.id OR"
            + " t.destination_account_id=a.id) AND t.date<=CURRENT_DATE),0) AS balance FROM account"
            + " a WHERE a.user_id=? ORDER BY a.name",
        user(jwt));
  }

  @PostMapping("/accounts")
  public Map<String, Object> account(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Account request) {
    var id = UUID.randomUUID();
    db.update(
        "INSERT INTO account(id,user_id,name,type,opening_balance) VALUES(?,?,?,?,?)",
        id,
        user(jwt),
        request.name(),
        request.type(),
        request.openingBalance());
    return Map.of("id", id);
  }

  @GetMapping("/categories")
  @Transactional
  public List<Map<String, Object>> categories(@AuthenticationPrincipal Jwt jwt) {
    var owner = user(jwt);
    Boolean initialized =
        db.queryForObject(
            "SELECT categories_initialized FROM app_user WHERE id=? FOR UPDATE",
            Boolean.class,
            owner);
    if (!Boolean.TRUE.equals(initialized)) {
      for (String name : CategoryDefaults.NAMES) {
        if (db.queryForObject(
                "SELECT COUNT(*) FROM category WHERE user_id=? AND lower(name)=lower(?)",
                Integer.class,
                owner,
                name)
            == 0)
          db.update(
              "INSERT INTO category(id,user_id,name) VALUES(?,?,?)",
              UUID.randomUUID(),
              owner,
              name);
      }
      db.update("UPDATE app_user SET categories_initialized=true WHERE id=?", owner);
    }
    return db.queryForList("SELECT id,name FROM category WHERE user_id=? ORDER BY name", user(jwt));
  }

  @PostMapping("/categories")
  public Map<String, Object> category(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Category request) {
    if (db.queryForObject(
            "SELECT COUNT(*) FROM category WHERE user_id=? AND lower(name)=lower(?)",
            Integer.class,
            user(jwt),
            request.name().trim())
        > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria já cadastrada.");
    var id = UUID.randomUUID();
    db.update(
        "INSERT INTO category(id,user_id,name) VALUES(?,?,?)",
        id,
        user(jwt),
        request.name().trim());
    return Map.of("id", id);
  }

  @PatchMapping("/categories/{id}")
  public void renameCategory(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody Category request) {
    var owner = user(jwt);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM category WHERE user_id=? AND id<>? AND lower(name)=lower(?)",
            Integer.class,
            owner,
            id,
            request.name().trim())
        > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria já cadastrada.");
    if (db.update(
            "UPDATE category SET name=? WHERE id=? AND user_id=?", request.name().trim(), id, owner)
        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada.");
  }

  @Transactional
  @DeleteMapping("/categories/{id}")
  public void deleteCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    var owner = user(jwt);
    owned("category", id, owner);
    db.update(
        "UPDATE financial_transaction SET category_id=NULL WHERE category_id=? AND user_id=?",
        id,
        owner);
    db.update("DELETE FROM budget_allocation WHERE category_id=? AND user_id=?", id, owner);
    db.update("DELETE FROM category WHERE id=? AND user_id=?", id, owner);
  }

  @GetMapping("/transactions/{id}")
  public Map<String, Object> details(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    var rows =
        db.queryForList(
            "SELECT * FROM financial_transaction WHERE id=? AND user_id=?", id, user(jwt));
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return rows.getFirst();
  }

  @GetMapping("/transactions")
  public List<Map<String, Object>> transactions(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String month) {
    var sql =
        new StringBuilder(
            "SELECT t.*,c.name AS category_name FROM financial_transaction t LEFT JOIN category c"
                + " ON c.id=t.category_id WHERE t.user_id=?");
    var parameters = new ArrayList<Object>();
    parameters.add(user(jwt));
    if (categoryId != null) {
      sql.append(" AND t.category_id=?");
      parameters.add(categoryId);
    }
    if (month != null) {
      var start = java.time.YearMonth.parse(month).atDay(1);
      sql.append(" AND t.date>=? AND t.date<?");
      parameters.add(start);
      parameters.add(start.plusMonths(1));
    }
    sql.append(" ORDER BY t.date DESC,t.id LIMIT 100 OFFSET ?");
    parameters.add(Math.multiplyExact(page, 100));
    return db.queryForList(sql.toString(), parameters.toArray());
  }

  private void owned(String table, UUID id, UUID owner) {
    if (id == null) return;
    if (db.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE id=? AND user_id=?", Integer.class, id, owner)
        != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
  }

  private void validate(Transaction r, UUID owner) {
    new com.finor.planning.Ledger.Entry(
        r.accountId(), r.destinationAccountId(), r.type(), r.amount(), r.date());
    owned("account", r.accountId(), owner);
    owned("account", r.destinationAccountId(), owner);
    owned("category", r.categoryId(), owner);
    if (r.type() == com.finor.planning.Ledger.Type.TRANSFER && r.categoryId() != null)
      throw new IllegalArgumentException();
  }

  @Transactional
  @PostMapping("/transactions")
  public Map<String, Object> create(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody Transaction r) {
    var owner = user(jwt);
    validate(r, owner);
    String fingerprint =
        r.accountId()
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
                    + java.util.Base64.getEncoder()
                        .encodeToString(
                            r.description().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    db.update(
        "INSERT INTO transaction_request(user_id,idempotency_key,fingerprint) VALUES(?,?,?) ON"
            + " CONFLICT DO NOTHING",
        owner,
        key,
        fingerprint);
    var request =
        db.queryForMap(
            "SELECT * FROM transaction_request WHERE user_id=? AND idempotency_key=? FOR UPDATE",
            owner,
            key);
    if (!request.get("fingerprint").equals(fingerprint))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Chave já utilizada com outros dados.");
    try {
      if (request.get("response_json") != null)
        return json.readValue(
            request.get("response_json").toString(),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
      var id = UUID.randomUUID();
      db.update(
          "INSERT INTO"
              + " financial_transaction(id,user_id,account_id,destination_account_id,category_id,type,amount,date,description,idempotency_key)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?)",
          id,
          owner,
          r.accountId(),
          r.destinationAccountId(),
          r.categoryId(),
          r.type().name(),
          r.amount(),
          r.date(),
          r.description(),
          key);
      var row =
          db.queryForMap("SELECT * FROM financial_transaction WHERE id=? AND user_id=?", id, owner);
      db.update(
          "UPDATE transaction_request SET response_json=? WHERE user_id=? AND idempotency_key=?",
          json.writeValueAsString(row),
          owner,
          key);
      return row;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Falha ao processar requisição.");
    }
  }

  @Transactional
  @PatchMapping("/transactions/{id}")
  public void edit(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Transaction r) {
    var owner = user(jwt);
    owned("financial_transaction", id, owner);
    validate(r, owner);
    db.update(
        "UPDATE financial_transaction SET"
            + " account_id=?,destination_account_id=?,category_id=?,type=?,amount=?,date=?,description=?"
            + " WHERE id=? AND user_id=?",
        r.accountId(),
        r.destinationAccountId(),
        r.categoryId(),
        r.type().name(),
        r.amount(),
        r.date(),
        r.description(),
        id,
        owner);
  }

  @DeleteMapping("/transactions/{id}")
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    if (db.update("DELETE FROM financial_transaction WHERE id=? AND user_id=?", id, user(jwt)) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  @GetMapping("/dashboard/summary")
  public Map<String, Object> summary(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String month) {
    var start =
        month == null
            ? LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).withDayOfMonth(1)
            : java.time.YearMonth.parse(month).atDay(1);
    var totals =
        db.queryForMap(
            "SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amount ELSE 0 END),0) AS"
                + " income,COALESCE(SUM(CASE WHEN type='EXPENSE' THEN amount ELSE 0 END),0) AS"
                + " expenses FROM financial_transaction WHERE user_id=? AND date>=? AND date<?",
            user(jwt),
            start,
            start.plusMonths(1));
    totals.put("accounts", accounts(jwt));
    return totals;
  }
}
