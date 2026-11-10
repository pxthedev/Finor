package com.finor.planning;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.context.annotation.Profile("!firestore")
@RestController
@RequestMapping("/api/v1/planning")
public class SuggestionController {
  private final PlanningController planning;

  public SuggestionController(PlanningController planning) {
    this.planning = planning;
  }

  @PostMapping("/suggestions")
  public Map<String, Object> suggest(@AuthenticationPrincipal Jwt jwt) {
    var rows = planning.schedule(jwt);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Configure sua renda primeiro.");
    var row = rows.getFirst();
    var income = (BigDecimal) row.get("monthly_amount");
    var bills =
        planning.bills(jwt).stream()
            .filter(
                b ->
                    !(Boolean) b.get("paid")
                        && ((java.sql.Date) b.get("due_date"))
                            .toLocalDate()
                            .isBefore(((java.sql.Date) row.get("next_income")).toLocalDate()))
            .map(b -> (BigDecimal) b.get("amount"))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    var essentials = (BigDecimal) row.get("remaining_essentials");
    var margin = (BigDecimal) row.get("safety_margin");
    var free = income.subtract(bills).subtract(essentials).subtract(margin);
    return Map.of(
        "income",
        income,
        "obligations",
        bills,
        "essentials",
        essentials,
        "margin",
        margin,
        "unallocated",
        free,
        "notice",
        free.signum() < 0
            ? "As obrigações superam a renda informada. Revise os valores antes de reservar para"
                  + " metas."
            : "Revise as obrigações e escolha quanto destinar às metas. Esta sugestão não altera"
                  + " seus registros.");
  }
}

