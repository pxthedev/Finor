package com.finor.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Derives balances from the ledger, without mutable cached balances. */
public final class Ledger {
  private Ledger() {}

  public enum Type {
    INCOME,
    EXPENSE,
    TRANSFER
  }

  public record Entry(
      UUID account, UUID destination, Type type, BigDecimal amount, LocalDate date) {
    public Entry {
      if (account == null
          || type == null
          || date == null
          || amount == null
          || amount.signum() <= 0
          || amount.scale() > 2) throw new IllegalArgumentException("Transação inválida.");
      if (type == Type.TRANSFER
          ? destination == null || destination.equals(account)
          : destination != null) throw new IllegalArgumentException("Destino inválido.");
    }
  }

  public static BigDecimal balance(
      UUID account, BigDecimal opening, List<Entry> entries, LocalDate through) {
    BigDecimal result = opening;
    for (var entry : entries) {
      if (entry.date().isAfter(through)) continue;
      if (entry.account().equals(account))
        result =
            entry.type() == Type.INCOME
                ? result.add(entry.amount())
                : result.subtract(entry.amount());
      if (account.equals(entry.destination())) result = result.add(entry.amount());
    }
    return result;
  }

  public static BigDecimal total(
      List<Entry> entries, Type type, LocalDate from, LocalDate through) {
    if (type == Type.TRANSFER)
      throw new IllegalArgumentException("Transferências não compõem receitas ou despesas.");
    return entries.stream()
        .filter(e -> e.type() == type && !e.date().isBefore(from) && !e.date().isAfter(through))
        .map(Entry::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
