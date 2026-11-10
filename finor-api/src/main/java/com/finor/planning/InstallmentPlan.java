package com.finor.planning;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

public final class InstallmentPlan {
  private InstallmentPlan() {}

  public record Input(
      @NotBlank @Size(max = 120) String name,
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal installmentAmount,
      @Min(1) @Max(360) int totalInstallments,
      @Min(0) int paidInstallments,
      @NotNull LocalDate firstDueDate) {}

  public record Payment(UUID accountId) {}

  public static void validate(Input input) {
    if (input.totalInstallments() < 1
        || input.totalInstallments() > 360
        || input.paidInstallments() < 0
        || input.paidInstallments() > input.totalInstallments())
      throw new IllegalArgumentException("Confira a quantidade de parcelas.");
  }

  public static UUID paymentKey(String owner, String plan, int number) {
    return UUID.nameUUIDFromBytes(
        ("finor:installment:" + owner + ":" + plan + ":" + number)
            .getBytes(StandardCharsets.UTF_8));
  }
}
