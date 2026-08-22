package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.client.WalletClient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LockedForfeitTest {

  @Test
  void forfeitsOnlyTheLostExposureWithStableKey() {
    WalletClient wallet = mock(WalletClient.class);
    SettlementWalletExecutor executor = new SettlementWalletExecutor(wallet);
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    SettlementAttempt attempt =
        SettlementAttempt.resolved(
            betId,
            UUID.randomUUID(),
            SettlementResult.LOST,
            new SettlementMoneyPlan(
                Money.krw(3000), Money.krw(1000), Money.krw(1000), Money.krw(2000), Money.krw(0)),
            new SettlementLease(UUID.randomUUID(), Instant.MAX),
            Instant.EPOCH);
    UUID operationId = UUID.randomUUID();
    when(wallet.forfeit("settle:forfeit:" + betId, userId, Money.krw(2000)))
        .thenReturn(operationId);

    assertThat(executor.forfeitLocked(attempt, userId)).contains(operationId);

    verify(wallet).forfeit("settle:forfeit:" + betId, userId, Money.krw(2000));
  }
}
