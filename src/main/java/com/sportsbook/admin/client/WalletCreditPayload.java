package com.sportsbook.admin.client;

import com.sportsbook.protocol.value.Money;
import java.util.Objects;
import java.util.UUID;

public record WalletCreditPayload(UUID userId, Money amount, Source source, Reason reason) {

  public WalletCreditPayload {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(reason, "reason");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Refund amount must be strictly positive");
    }
  }

  public static WalletCreditPayload refund(UUID userId, Money amount) {
    return new WalletCreditPayload(userId, amount, Source.HOUSE_POOL, Reason.REFUND);
  }

  public enum Source {
    HOUSE_POOL
  }

  public enum Reason {
    REFUND
  }
}
