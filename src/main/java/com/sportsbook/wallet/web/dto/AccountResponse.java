package com.sportsbook.wallet.web.dto;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public account snapshot without recovery queue internals. */
public record AccountResponse(
    UUID userId,
    Currency currency,
    Money available,
    Money locked,
    boolean outboundFrozen,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  public static AccountResponse from(Account account) {
    Objects.requireNonNull(account, "account");
    return new AccountResponse(
        account.userId(),
        account.currency(),
        account.available(),
        account.locked(),
        account.isOutboundFrozen(),
        account.version(),
        account.createdAt(),
        account.updatedAt());
  }
}
