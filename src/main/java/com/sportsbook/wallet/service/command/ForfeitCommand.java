package com.sportsbook.wallet.service.command;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.Objects;
import java.util.UUID;

/** Transfers a losing stake from the user's locked balance to the house. */
public record ForfeitCommand(UUID userId, Money amount, IdempotencyKey idempotencyKey) {

  public ForfeitCommand {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Forfeit amount must be strictly positive");
    }
  }
}
