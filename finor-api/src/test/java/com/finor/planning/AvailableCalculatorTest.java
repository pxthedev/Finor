package com.finor.planning;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailableCalculatorTest {
  BigDecimal money(String value) {
    return new BigDecimal(value);
  }

  @Test
  void excludesPaidAndFutureBillsButIncludesOverdue() {
    var today = LocalDate.of(2026, 11, 7);
    var bills =
        List.of(
            new AvailableCalculator.Obligation(money("100"), false, today.minusDays(1)),
            new AvailableCalculator.Obligation(money("200"), true, today),
            new AvailableCalculator.Obligation(money("400"), false, today.plusDays(10)));
    var result =
        AvailableCalculator.calculate(
            money("1000"),
            bills,
            money("100"),
            money("200"),
            money("50"),
            today,
            today.plusDays(10));
    assertEquals(money("550.00"), result.available());
    assertEquals(money("55.00"), result.suggestedDaily());
  }

  @Test
  void preservesDeficitAndNeverSuggestsNegativeSpending() {
    var today = LocalDate.of(2026, 11, 7);
    var result =
        AvailableCalculator.calculate(
            money("10"), List.of(), money("100"), money("0"), money("0"), today, today.plusDays(3));
    assertEquals(money("-90.00"), result.available());
    assertEquals(money("0.00"), result.suggestedDaily());
  }

  @Test
  void rejectsInvalidIncomeDate() {
    var today = LocalDate.now();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AvailableCalculator.calculate(
                money("0"), List.of(), money("0"), money("0"), money("0"), today, today));
  }
}
