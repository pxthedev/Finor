package com.finor.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finor.planning.PlanningController;
import com.finor.transaction.FinanceController;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
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

@org.springframework.context.annotation.Profile("!firestore")
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
  private final JdbcTemplate db;
  private final AssistantProvider provider;
  private final ObjectMapper json;
  private final FinanceController finance;
  private final PlanningController planning;
  private final Validator validator;

  public AssistantController(
      JdbcTemplate db,
      AssistantProvider provider,
      ObjectMapper json,
      FinanceController finance,
      PlanningController planning,
      Validator validator) {
    this.db = db;
    this.provider = provider;
    this.json = json;
    this.finance = finance;
    this.planning = planning;
    this.validator = validator;
  }

  public record Message(UUID sessionId, @NotBlank @Size(max = 2000) String message) {}

  UUID user(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  @GetMapping("/sessions")
  public List<Map<String, Object>> sessions(@AuthenticationPrincipal Jwt jwt) {
    return db.queryForList(
        "SELECT id,created_at FROM chat_session WHERE user_id=? ORDER BY created_at DESC",
        user(jwt));
  }

  @GetMapping("/sessions/{id}/messages")
  public List<Map<String, Object>> messages(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return db.queryForList(
        "SELECT id,role,content,created_at FROM chat_message WHERE user_id=? AND session_id=? ORDER"
            + " BY created_at",
        user(jwt),
        id);
  }

  @GetMapping("/sessions/{id}/actions")
  public List<Map<String, Object>> actions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return db.queryForList(
        "SELECT id,payload,status FROM assistant_action WHERE user_id=? AND session_id=? ORDER BY"
            + " created_at",
        user(jwt),
        id);
  }

  @GetMapping("/sessions/{id}/timeline")
  public Object timeline(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return Map.of("messages", messages(jwt, id), "actions",
        db.queryForList("SELECT id,payload,status,created_at FROM assistant_action WHERE user_id=? AND session_id=? ORDER BY created_at", user(jwt), id));
  }

  @PostMapping("/messages")
  public Map<String, Object> send(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Message r)
      throws Exception {
    UUID owner = user(jwt), session = r.sessionId();
    if (session == null) {
      session = UUID.randomUUID();
      db.update("INSERT INTO chat_session(id,user_id) VALUES(?,?)", session, owner);
    } else if (db.queryForObject(
            "SELECT COUNT(*) FROM chat_session WHERE id=? AND user_id=?",
            Integer.class,
            session,
            owner)
        != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM chat_message WHERE user_id=? AND created_at>now()-interval '1"
                + " hour'",
            Integer.class,
            owner)
        >= 60) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
    com.fasterxml.jackson.databind.JsonNode intent;
    try {
      intent = provider.interpret(r.message(), finance.categories(jwt).stream().map(c -> c.get("name").toString()).toList());
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Não foi possível interpretar a mensagem. Tente novamente.");
    }
    String reply;
    Map<String, Object> action = null;
    switch (intent.path("intent").asText()) {
      case "available" -> {
        var available = planning.available(jwt);
        reply =
            "Disponível até o próximo recebimento: R$ "
                + available.available().toPlainString()
                + ". Limite diário sugerido: R$ "
                + available.suggestedDaily().toPlainString()
                + ". Cálculo baseado nas previsões registradas.";
      }
      case "expenses" -> {
        var name = intent.path("category").asText();
        if (name.isBlank()) {
          var summary = finance.summary(jwt, null);
          reply = "Despesas registradas neste mês: R$ " + summary.get("expenses") + ".";
        } else {
          var matches =
              finance.categories(jwt).stream()
                  .filter(c -> c.get("name").toString().equalsIgnoreCase(name))
                  .toList();
          if (matches.isEmpty()) {
            reply =
                "Não encontrei essa categoria nos seus registros. Consulte suas categorias em"
                    + " Mais.";
          } else {
            var start = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).withDayOfMonth(1);
            var total =
                db.queryForObject(
                    "SELECT COALESCE(SUM(amount),0) FROM financial_transaction WHERE user_id=? AND"
                        + " category_id=? AND type='EXPENSE' AND date>=? AND date<?",
                    BigDecimal.class,
                    owner,
                    matches.getFirst().get("id"),
                    start,
                    start.plusMonths(1));
            reply =
                "Despesas com "
                    + matches.getFirst().get("name")
                    + " neste mês: R$ "
                    + total.toPlainString()
                    + ".";
          }
        }
      }
      case "simulate" -> {
        var amount = intent.path("amount").decimalValue();
        var purchase = new PlanningController.Purchase(amount);
        if (!validator.validate(purchase).isEmpty()) {
          reply = "Qual o valor da compra?";
          break;
        }
        var result = planning.simulate(jwt, purchase);
        reply =
            "Após a compra restariam R$ " + result.get("remaining") + ". " + result.get("notice");
      }
      case "planning" -> {
        var schedules = planning.schedule(jwt);
        reply =
            schedules.isEmpty()
                ? "Configure sua renda e suas obrigações em Gastos e Metas para distribuir seu salário."
                : "Sua renda mensal registrada é R$ "
                    + schedules.getFirst().get("monthly_amount")
                    + ". Priorize contas pendentes e essenciais; depois revise a margem e suas"
                    + " metas em Gastos e Metas. Não alterei seus registros.";
      }
      case "savings" -> {
        reply =
            "Você pode criar uma meta e ajustar o valor reservado na área Metas financeiras. Antes"
                + " de reservar, revise o disponível e mantenha suas contas previstas atualizadas."
                + " Não alterei seus registros.";
      }
      case "transaction" -> {
        var accounts = finance.accounts(jwt);
        var amount = intent.path("amount").decimalValue();
        var type = intent.path("type").asText();
        var description = intent.path("description").asText("");
        if (accounts.size() != 1
            || amount.signum() <= 0
            || amount.scale() > 2
            || amount.precision() > 19
            || !List.of("INCOME", "EXPENSE").contains(type)
            || description.length() > 300) {
          reply =
              "Informe o valor e se é receita ou despesa. Para escolher entre várias contas, use o"
                  + " registro manual.";
          break;
        }
        var categoryName = intent.path("category").asText();
        var resolved = AssistantCategory.resolve(categoryName, finance.categories(jwt),
            name -> (UUID) finance.category(jwt, new FinanceController.Category(name)).get("id"));
        UUID category = resolved.id();
        LocalDate date = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        if (intent.hasNonNull("date")) date = LocalDate.parse(intent.path("date").asText());
        var payload =
            new FinanceController.Transaction(
                (UUID) accounts.getFirst().get("id"),
                null,
                category,
                com.finor.planning.Ledger.Type.valueOf(type),
                amount,
                date,
                description);
        if (!validator.validate(payload).isEmpty()) throw new IllegalArgumentException();
        var id = UUID.randomUUID();
        db.update(
            "INSERT INTO assistant_action(id,user_id,session_id,payload) VALUES(?,?,?,?)",
            id,
            owner,
            session,
            json.writeValueAsString(payload));
        action = Map.of("id", id, "transaction", payload);
        reply = "Categoria: " + resolved.name() + ". Confira a transação antes de confirmar. Nenhum valor foi registrado ainda.";
      }
      default ->
          reply =
              "Posso ajudar a registrar uma receita ou despesa, consultar despesas do mês ou"
                  + " calcular quanto está disponível. Informe o valor quando quiser registrar uma"
                  + " transação.";
    }
    db.update(
        "INSERT INTO chat_message(id,user_id,session_id,role,content) VALUES(?,?,?,?,?)",
        UUID.randomUUID(),
        owner,
        session,
        "user",
        r.message());
    db.update(
        "INSERT INTO chat_message(id,user_id,session_id,role,content) VALUES(?,?,?,?,?)",
        UUID.randomUUID(),
        owner,
        session,
        "assistant",
        reply);
    var result = new HashMap<String, Object>();
    result.put("sessionId", session);
    result.put("reply", reply);
    result.put("action", action);
    return result;
  }

  @Transactional
  @PostMapping("/actions/{id}/confirm")
  public Map<String, Object> confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id)
      throws Exception {
    var rows =
        db.queryForList(
            "SELECT * FROM assistant_action WHERE id=? AND user_id=? FOR UPDATE", id, user(jwt));
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    var row = rows.getFirst();
    if (row.get("status").equals("REJECTED"))
      throw new ResponseStatusException(HttpStatus.CONFLICT);
    var payload =
        json.readValue(row.get("payload").toString(), FinanceController.Transaction.class);
    if (!validator.validate(payload).isEmpty()) throw new IllegalArgumentException();
    var transaction = finance.create(jwt, id, payload);
    db.update(
        "UPDATE assistant_action SET status='CONFIRMED' WHERE id=? AND user_id=?", id, user(jwt));
    return transaction;
  }

  @PostMapping("/actions/{id}/reject")
  public void reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    if (db.update(
            "UPDATE assistant_action SET status='REJECTED' WHERE id=? AND user_id=? AND"
                + " status='PENDING'",
            id,
            user(jwt))
        != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ação indisponível.");
  }
}


