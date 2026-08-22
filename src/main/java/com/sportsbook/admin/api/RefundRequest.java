package com.sportsbook.admin.api;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RefundRequest(
    @Positive long amount, @NotNull Currency currency, @NotBlank @Size(max = 256) String reason) {

  public RefundRequest {
    if (reason != null) {
      reason = reason.trim();
    }
  }

  public Money money() {
    return new Money(amount, currency);
  }
}
