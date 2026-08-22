package com.sportsbook.betting.client;

import com.sportsbook.protocol.value.Money;
import java.util.Objects;
import java.util.UUID;

public record WalletDebitRequest(UUID userId, Money amount) {
  public WalletDebitRequest {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(amount, "amount");
  }
}
