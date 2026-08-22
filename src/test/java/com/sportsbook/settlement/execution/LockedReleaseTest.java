package com.sportsbook.settlement.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.client.WalletClient;
import com.sportsbook.settlement.client.WalletCreditPurpose;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LockedReleaseTest {

  @Test
  void usesRefundForResultsAndVoidOnlyForWholeSlipLifecycle() {
    WalletClient wallet = mock(WalletClient.class);
    when(wallet.credit(anyString(), any(), any(), any())).thenReturn(UUID.randomUUID());
    SettlementWalletExecutor executor = new SettlementWalletExecutor(wallet);
    UUID userId = UUID.randomUUID();
    UUID settledBetId = UUID.randomUUID();
    UUID voidedBetId = UUID.randomUUID();

    executor.releaseLocked(resolved(settledBetId), userId);
    executor.releaseLocked(voided(voidedBetId), userId);

    verify(wallet)
        .credit(
            "settle:refund:" + settledBetId,
            userId,
            Money.krw(1000),
            WalletCreditPurpose.RETURNED_STAKE);
    verify(wallet)
        .credit(
            "void:refund:" + voidedBetId,
            userId,
            Money.krw(3000),
            WalletCreditPurpose.WHOLE_SLIP_VOID);
  }

  private static SettlementAttempt resolved(UUID betId) {
    Money zero = Money.krw(0);
    return new SettlementAttempt(
        betId,
        SettlementAttempt.Action.SETTLE,
        UUID.randomUUID(),
        SettlementResult.VOID,
        null,
        new SettlementMoneyPlan(
            Money.krw(3000), Money.krw(1000), Money.krw(1000), Money.krw(2000), zero),
        new SettlementLease(UUID.randomUUID(), Instant.MAX),
        1,
        null,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private static SettlementAttempt voided(UUID betId) {
    return SettlementAttempt.wholeSlipVoid(
        betId,
        UUID.randomUUID(),
        "EVENT_CANCELLED",
        Money.krw(3000),
        new SettlementLease(UUID.randomUUID(), Instant.MAX),
        Instant.EPOCH);
  }
}
