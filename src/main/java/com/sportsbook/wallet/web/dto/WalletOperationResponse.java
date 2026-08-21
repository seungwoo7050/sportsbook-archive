package com.sportsbook.wallet.web.dto;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.service.WalletOperationResult;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public view of one authoritative matched ledger transfer. */
public record WalletOperationResponse(
    UUID operationGroupId, UUID userId, Money amount, LedgerReason reason, Instant at) {

  public static WalletOperationResponse from(WalletOperationResult result) {
    Objects.requireNonNull(result, "result");
    return new WalletOperationResponse(
        result.operationGroupId(), result.userId(), result.amount(), result.reason(), result.at());
  }
}
