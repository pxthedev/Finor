package com.finor.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class AvailableCalculator {
  private AvailableCalculator() {}

  public record Obligation(BigDecimal amount, boolean paid, LocalDate dueDate) {}

  public record Result(
      BigDecimal balance,
      BigDecimal pending,
      BigDecimal essentials,
      BigDecimal reserves,
      BigDecimal margin,
      BigDecimal available,
      BigDecimal suggestedDaily,
      long days) {}

  public static Result calculate(
      BigDecimal balance,
      List<Obligation> obligations,
      BigDecimal remainingEssentials,
      BigDecimal protectedReserves,
      BigDecimal margin,
      LocalDate today,
      LocalDate nextIncome) {
    if (nextIncome == null || !nextIncome.isAfter(today))
      throw new IllegalArgumentException("O próximo recebimento deve ser posterior a hoje.");
    for (BigDecimal value : List.of(remainingEssentials, protectedReserves, margin))
      if (value.signum() < 0)
        throw new IllegalArgumentException("Valores protegidos não podem ser negativos.");
    BigDecimal pending = BigDecimal.ZERO;
    for (Obligation item : obligations) {
      if (item.amount().signum() < 0)
        throw new IllegalArgumentException("Conta com valor negativo.");
      if (!item.paid() && item.dueDate().isBefore(nextIncome)) pending = pending.add(item.amount());
    }
    BigDecimal available =
        balance
            .subtract(pending)
            .subtract(remainingEssentials)
            .subtract(protectedReserves)
            .subtract(margin)
            .setScale(2, RoundingMode.HALF_EVEN);
    long days = ChronoUnit.DAYS.between(today, nextIncome);
    return new Result(
        balance,
        pending,
        remainingEssentials,
        protectedReserves,
        margin,
        available,
        available.max(BigDecimal.ZERO).divide(BigDecimal.valueOf(days), 2, RoundingMode.DOWN),
        days);
  }
}
