package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementExecutionTest {

  @Test
  void couplesAClaimedAttemptOnlyToItsWalletUser() {
    SettlementAttempt attempt =
        SettlementAttempt.resolved(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            new SettlementMoneyPlan(
                Money.krw(1000), Money.krw(2000), Money.krw(1000), Money.krw(0), Money.krw(1000)),
            new SettlementLease(UUID.randomUUID(), Instant.MAX),
            Instant.EPOCH);
    UUID userId = UUID.randomUUID();

    assertThat(new SettlementExecution(attempt, userId))
        .isEqualTo(new SettlementExecution(attempt, userId));
  }
}
