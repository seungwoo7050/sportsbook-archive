package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.value.Money;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WholeSlipVoidAttemptTest {

  @Test
  void reservesVoidActionForLifecycleOrManualWholeSlipRefunds() {
    Instant now = Instant.parse("2026-08-22T00:00:00Z");

    SettlementAttempt attempt =
        SettlementAttempt.wholeSlipVoid(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EVENT_CANCELLED",
            Money.krw(3000),
            SettlementLease.acquire(now, Duration.ofSeconds(30)),
            now);

    assertThat(attempt.action()).isEqualTo(SettlementAttempt.Action.VOID);
    assertThat(attempt.result()).isNull();
    assertThat(attempt.voidReason()).isEqualTo("EVENT_CANCELLED");
    assertThat(attempt.money().lockedRelease()).isEqualTo(Money.krw(3000));
    assertThat(attempt.money().lockedForfeit()).isEqualTo(Money.krw(0));
    assertThat(attempt.money().houseProfit()).isEqualTo(Money.krw(0));
  }

  @Test
  void rejectsMarketVoidAsAWholeSlipTerminalReason() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                SettlementAttempt.wholeSlipVoid(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "MARKET_VOID",
                    Money.krw(100),
                    SettlementLease.acquire(Instant.EPOCH, Duration.ofSeconds(30)),
                    Instant.EPOCH));
  }
}
