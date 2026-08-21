package com.sportsbook.wallet.service.command;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.Objects;
import java.util.UUID;

/** Credits available funds from locked stake or the house pool. */
public record CreditCommand(
    UUID userId, Money amount, Source source, CreditReason reason, IdempotencyKey idempotencyKey) {

  public CreditCommand {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Credit amount must be strictly positive");
    }
  }

  public enum Source {
    USER_LOCKED,
    HOUSE_POOL
  }
}
