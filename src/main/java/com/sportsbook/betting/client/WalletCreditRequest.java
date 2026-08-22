package com.sportsbook.betting.client;

import com.sportsbook.protocol.value.Money;
import java.util.Objects;
import java.util.UUID;

public record WalletCreditRequest(UUID userId, Money amount, String source, String reason) {

  static WalletCreditRequest refund(UUID userId, Money amount) {
    return new WalletCreditRequest(userId, amount, "USER_LOCKED", "REFUND");
  }

  public WalletCreditRequest {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(reason, "reason");
  }
}
