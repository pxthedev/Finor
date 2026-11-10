package com.finor.planning;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerTest {
  @Test
  void transferPreservesCombinedBalanceAndDoesNotCountAsExpense() {
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var date = LocalDate.of(2026, 11, 7);
    var entries =
        List.of(new Ledger.Entry(a, b, Ledger.Type.TRANSFER, new BigDecimal("40.00"), date));
    var first = Ledger.balance(a, new BigDecimal("100.00"), entries, date);
    var second = Ledger.balance(b, BigDecimal.ZERO, entries, date);
    assertEquals(new BigDecimal("60.00"), first);
    assertEquals(new BigDecimal("100.00"), first.add(second));
    assertEquals(0, Ledger.total(entries, Ledger.Type.EXPENSE, date, date).signum());
  }

  @Test
  void rejectsTransferToSameAccount() {
    var id = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class,
        () -> new Ledger.Entry(id, id, Ledger.Type.TRANSFER, BigDecimal.ONE, LocalDate.now()));
  }

  @Test
  void futureIncomeIsNotSpendable() {
    var id = UUID.randomUUID();
    var today = LocalDate.now();
    var entries =
        List.of(new Ledger.Entry(id, null, Ledger.Type.INCOME, BigDecimal.TEN, today.plusDays(1)));
    assertEquals(0, Ledger.balance(id, BigDecimal.ZERO, entries, today).signum());
  }
}
