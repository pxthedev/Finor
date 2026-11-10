package com.finor.budget;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExpenseOverviewTest {
  Map<String, Object> entry(String type, String amount, String date, String category) {
    var row = new LinkedHashMap<String, Object>();
    row.put("type", type); row.put("amount", amount); row.put("date", date); row.put("category_id", category);
    return row;
  }

  @Test void aggregatesEntireHistoryWithoutPaginationAndSeparatesMonthlyValues() {
    var entries = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 205; i++) entries.add(entry("EXPENSE", "0.10", "2025-01-01", "food"));
    entries.add(entry("EXPENSE", "5.25", "2026-11-02", "food"));
    entries.add(entry("EXPENSE", "3.50", "2026-11-03", null));
    entries.add(entry("EXPENSE", "2.00", "2026-10-03", "deleted-category"));
    entries.add(entry("INCOME", "100.00", "2026-11-02", null));
    entries.add(entry("TRANSFER", "999.00", "2026-11-02", null));
    var report = ExpenseOverview.calculate(entries, List.of(Map.of("id", "food", "name", "Alimentação")), YearMonth.of(2026, 11));
    var summary = (Map<?, ?>)report.get("summary");
    assertEquals(new BigDecimal("100.00"), summary.get("income"));
    assertEquals(new BigDecimal("8.75"), summary.get("expenses"));
    var chart = (List<Map<String, Object>>)report.get("allTimeExpenses");
    assertEquals(2, chart.size());
    assertEquals(new BigDecimal("25.75"), chart.getFirst().get("spent"));
    assertEquals("Sem categoria", chart.getLast().get("name"));
    assertEquals(new BigDecimal("5.50"), chart.getLast().get("spent"));
    var evolution = (List<Map<String, Object>>)report.get("evolution");
    assertEquals(6, evolution.size());
    assertEquals("2026-06", evolution.getFirst().get("month"));
    assertEquals("2026-11", evolution.getLast().get("month"));
    assertEquals(new BigDecimal("8.75"), evolution.getLast().get("expenses"));
  }

  @Test void emptyHistoryKeepsCategoriesAndZeroMonths() {
    var report = ExpenseOverview.calculate(List.of(), List.of(Map.of("id", "food", "name", "Alimentação")), YearMonth.of(2026, 11));
    assertTrue(((List<?>)report.get("allTimeExpenses")).isEmpty());
    assertEquals(1, ((List<?>)report.get("categories")).size());
    assertEquals(BigDecimal.ZERO, ((Map<?, ?>)report.get("summary")).get("expenses"));
  }
}
