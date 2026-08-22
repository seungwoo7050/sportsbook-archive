package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.lifecycle.LifecycleObservation;
import com.sportsbook.settlement.lifecycle.LifecycleStore;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresLifecycleIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private LifecycleStore lifecycles;
  @Autowired private SettlementAttemptRepository attempts;

  @Test
  void persistsTypedTimestampsAndDoesNotStarveUnclaimedTombstones() {
    Instant now = Instant.parse("2026-08-22T01:00:00Z");
    UUID claimedEvent = UUID.randomUUID();
    UUID actionableEvent = UUID.randomUUID();
    PendingBet claimed = insertPendingBet(claimedEvent);
    insertPendingBet(actionableEvent);
    LifecycleObservation scheduled =
        LifecycleObservation.observe(
            claimedEvent,
            EventLifecycleStatus.SCHEDULED,
            now.minusSeconds(60),
            now.plusSeconds(3600),
            now);
    LifecycleObservation cancelled =
        LifecycleObservation.observe(
            claimedEvent, EventLifecycleStatus.CANCELLED, now, null, now.plusSeconds(1));
    LifecycleObservation postponed =
        LifecycleObservation.observe(
            actionableEvent, EventLifecycleStatus.POSTPONED, now, null, now.plusSeconds(2));

    assertThat(lifecycles.record(scheduled)).isEqualTo(LifecycleStore.RecordResult.OBSERVED);
    assertThat(lifecycles.record(cancelled))
        .isEqualTo(LifecycleStore.RecordResult.TERMINAL_LATCHED);
    assertThat(lifecycles.record(postponed))
        .isEqualTo(LifecycleStore.RecordResult.TERMINAL_LATCHED);
    assertThat(
            attempts.claimPending(
                SettlementAttemptDraft.wholeSlipVoid(
                    claimed.betId(), claimed.eventId(), "EVENT_CANCELLED", Money.krw(100)),
                Duration.ofSeconds(30)))
        .isPresent();

    assertThat(lifecycles.findTombstone(claimedEvent))
        .get()
        .extracting(LifecycleObservation::occurredAt, LifecycleObservation::receivedAt)
        .containsExactly(now, now.plusSeconds(1));
    assertThat(lifecycles.findActionableTombstones(1))
        .extracting(LifecycleObservation::eventId)
        .containsExactly(actionableEvent);
  }
}
