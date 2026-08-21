package com.sportsbook.wallet.service.command;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.util.Objects;
import java.util.UUID;

/** Stages available funds in the locked balance while a bet remains open. */
public record DebitCommand(UUID userId, Money amount, IdempotencyKey idempotencyKey) {

  public DebitCommand {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Debit amount must be strictly positive");
    }
  }
}
