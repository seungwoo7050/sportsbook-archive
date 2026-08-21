package com.sportsbook.wallet.service.command;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.Objects;
import java.util.UUID;

/** Moves external payment funds into the user's available balance. */
public record DepositCommand(UUID userId, Money amount, IdempotencyKey idempotencyKey) {

  public DepositCommand {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Deposit amount must be strictly positive");
    }
  }
}
