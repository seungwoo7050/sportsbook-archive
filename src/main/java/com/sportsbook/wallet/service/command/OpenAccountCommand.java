package com.sportsbook.wallet.service.command;

import com.sportsbook.protocol.value.Currency;
import java.util.Objects;
import java.util.UUID;

/** Opens or reuses the zero-balance wallet identified by a user UUID. */
public record OpenAccountCommand(UUID userId, Currency currency) {

  public OpenAccountCommand {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(currency, "currency");
  }
}
