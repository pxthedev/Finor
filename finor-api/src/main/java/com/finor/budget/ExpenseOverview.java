package com.finor.budget;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Builds the monthly report and the all-time chart in one pass over the ledger. */
public final class ExpenseOverview {
  private ExpenseOverview() {}

  public static Map<String, Object> calculate(List<Map<String, Object>> entries,
      List<Map<String, Object>> categories, YearMonth month) {
    var names = new LinkedHashMap<String, String>();
    for (var c : categories) names.put(c.get("id").toString(), c.get("name").toString());
    var monthly = new LinkedHashMap<YearMonth, BigDecimal[]>();
    var categoryTotals = new HashMap<String, BigDecimal>();
    var allTime = new LinkedHashMap<String, BigDecimal>();
    for (var entry : entries) {
      String type = Objects.toString(entry.get("type"), "");
      if (!type.equals("EXPENSE") && !type.equals("INCOME")) continue;
      var period = YearMonth.from(LocalDate.parse(entry.get("date").toString()));
      var amount = new BigDecimal(entry.get("amount").toString());
      var totals = monthly.computeIfAbsent(period, p -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
      int index = type.equals("INCOME") ? 0 : 1;
      totals[index] = totals[index].add(amount);
      if (index == 1) {
        String category = Objects.toString(entry.get("category_id"), "");
        if (!names.containsKey(category)) category = "";
        allTime.merge(category, amount, BigDecimal::add);
        if (period.equals(month)) categoryTotals.merge(category, amount, BigDecimal::add);
      }
    }
    var selected = monthly.getOrDefault(month, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
    var breakdown = new ArrayList<Map<String, Object>>();
    names.forEach((id, name) -> breakdown.add(Map.of("id", id, "name", name,
        "spent", categoryTotals.getOrDefault(id, BigDecimal.ZERO))));
    var chart = new ArrayList<Map<String, Object>>();
    allTime.forEach((id, spent) -> chart.add(Map.of("id", id, "name", names.getOrDefault(id, "Sem categoria"), "spent", spent)));
    chart.sort(Comparator.<Map<String, Object>, BigDecimal>comparing(c -> (BigDecimal)c.get("spent")).reversed()
        .thenComparing(c -> c.get("id").toString()));
    var evolution = new ArrayList<Map<String, Object>>();
    for (var period = month.minusMonths(5); !period.isAfter(month); period = period.plusMonths(1)) {
      var totals = monthly.getOrDefault(period, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
      evolution.add(Map.of("month", period.toString(), "income", totals[0], "expenses", totals[1]));
    }
    return Map.of("summary", Map.of("income", selected[0], "expenses", selected[1]),
        "categories", breakdown, "allTimeExpenses", chart, "evolution", evolution);
  }
}
