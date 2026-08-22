package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
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

class HouseProfitPayoutTest {

  @Test
  void creditsOnlyProfitFromHousePoolWithStableKey() {
    WalletClient wallet = mock(WalletClient.class);
    SettlementWalletExecutor executor = new SettlementWalletExecutor(wallet);
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    SettlementAttempt attempt =
        SettlementAttempt.resolved(
            betId,
            UUID.randomUUID(),
            SettlementResult.WON,
            new SettlementMoneyPlan(
                Money.krw(3000),
                Money.krw(26000),
                Money.krw(2000),
                Money.krw(1000),
                Money.krw(24000)),
            new SettlementLease(UUID.randomUUID(), Instant.MAX),
            Instant.EPOCH);
    UUID operationId = UUID.randomUUID();
    when(wallet.credit(
            "settle:payout:" + betId, userId, Money.krw(24000), WalletCreditPurpose.PROFIT_PAYOUT))
        .thenReturn(operationId);

    assertThat(executor.payHouseProfit(attempt, userId)).contains(operationId);
    verify(wallet)
        .credit(
            "settle:payout:" + betId, userId, Money.krw(24000), WalletCreditPurpose.PROFIT_PAYOUT);
  }
}
