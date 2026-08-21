package com.sportsbook.wallet.domain.error;

import com.sportsbook.protocol.value.Money;
import java.util.UUID;

/** Raised when a requested debit exceeds the addressed balance bucket. */
public final class InsufficientBalanceException extends RuntimeException {

  public InsufficientBalanceException(UUID userId, Money requested, Money available) {
    super(
        "Account "
            + userId
            + " cannot debit "
            + requested
            + " from available balance "
            + available);
  }
}
