package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SettlementAttemptDraftTest {

  @Test
  void becomesClaimedOnlyWithDatabaseOwnedLeaseTimes() {
    SettlementAttemptDraft draft =
        SettlementAttemptDraft.resolved(
            UUID.randomUUID(), UUID.randomUUID(), SettlementResult.WON, money());
    Instant created = Instant.parse("2026-08-22T00:00:00Z");
    Instant until = created.plusSeconds(30);

    SettlementAttempt claimed =
        draft.claimed(new SettlementLease(UUID.randomUUID(), until), created, created);

    assertThat(claimed.attemptCount()).isEqualTo(1);
    assertThat(claimed.createdAt()).isEqualTo(created);
    assertThat(claimed.lease().until()).isEqualTo(until);
  }

  @Test
  void keepsMarketVoidOutOfTheWholeSlipVoidPath() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                SettlementAttemptDraft.wholeSlipVoid(
                    UUID.randomUUID(), UUID.randomUUID(), "MARKET_VOID", Money.krw(100)));
  }

  private static SettlementMoneyPlan money() {
    return new SettlementMoneyPlan(
        Money.krw(100), Money.krw(200), Money.krw(100), Money.krw(0), Money.krw(100));
  }
}
