package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementAttemptTest {

  @Test
  void preparesCompleteResolvedPlanBeforeWalletWork() {
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    SettlementMoneyPlan money =
        new SettlementMoneyPlan(
            Money.krw(3000), Money.krw(26000), Money.krw(2000), Money.krw(1000), Money.krw(24000));

    SettlementAttempt attempt =
        SettlementAttempt.resolved(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            money,
            SettlementLease.acquire(now, Duration.ofSeconds(30)),
            now);

    assertThat(attempt.action()).isEqualTo(SettlementAttempt.Action.SETTLE);
    assertThat(attempt.result()).isEqualTo(SettlementResult.WON);
    assertThat(attempt.money()).isEqualTo(money);
    assertThat(attempt.attemptCount()).isEqualTo(1);
    assertThat(attempt.lastError()).isNull();
  }
}
