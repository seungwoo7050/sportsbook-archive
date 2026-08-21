package com.sportsbook.wallet.service.command;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.Objects;
import java.util.UUID;

/** Moves available funds from a user wallet to the external payment account. */
public record WithdrawCommand(UUID userId, Money amount, IdempotencyKey idempotencyKey) {

  public WithdrawCommand {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Withdrawal amount must be strictly positive");
    }
  }
}
