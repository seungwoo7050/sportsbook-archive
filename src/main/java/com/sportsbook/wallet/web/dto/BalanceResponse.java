package com.sportsbook.wallet.web.dto;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import java.util.Objects;
import java.util.UUID;

/** Public balance view without recovery debt details. */
public record BalanceResponse(
    UUID userId, Money available, Money locked, Money total, boolean outboundFrozen) {

  public static BalanceResponse from(Account account) {
    Objects.requireNonNull(account, "account");
    return new BalanceResponse(
        account.userId(),
        account.available(),
        account.locked(),
        account.total(),
        account.isOutboundFrozen());
  }
}
