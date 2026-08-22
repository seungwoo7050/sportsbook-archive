package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.correction.CorrectionTargetRepository;
import com.sportsbook.settlement.correction.RevisionPlan;
import com.sportsbook.settlement.correction.RevisionPlanRepository;
import com.sportsbook.settlement.correction.RevisionTarget;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresCorrectionTargetIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private CorrectionTargetRepository targets;
  @Autowired private RevisionPlanRepository revisions;

  @Test
  void returnsOnlyStaleSettledBetsWithoutAnOwnedNextRevision() {
    UUID eventId = UUID.randomUUID();
    PendingBet actionable = insertPendingBet(eventId);
    PendingBet current = insertPendingBet(eventId);
    PendingBet owned = insertPendingBet(eventId);
    Instant sourceTime = Instant.parse("2026-08-22T00:00:00Z");
    UUID oldCandidate =
        insertResultCandidate(
            eventId, actionable.selectionId(), SettlementResult.WON, sourceTime, "SUPERSEDED");
    UUID acceptedCandidate =
        insertResultCandidate(
            eventId, current.selectionId(), SettlementResult.LOST, sourceTime, "ACCEPTED");
    settleBet(actionable, oldCandidate, SettlementResult.WON, 200);
    settleBet(current, acceptedCandidate, SettlementResult.WON, 200);
    settleBet(owned, oldCandidate, SettlementResult.WON, 200);
    RevisionTarget target =
        new RevisionTarget(
            owned.betId(),
            1,
            owned.userId(),
            eventId,
            acceptedCandidate,
            SettlementResult.WON,
            Money.krw(200),
            new BetSlipType.Single(),
            Money.krw(100),
            List.of(
                new ResolvedSelection(
                    owned.selectionId(), Odds.ofDecimal("2.0000"), SettlementResult.LOST)),
            sourceTime);
    RevisionPlan plan =
        new RevisionPlan(
            UUID.randomUUID(), target, SettlementResult.LOST, Money.krw(0), sourceTime);
    assertThat(revisions.persist(plan, Duration.ofSeconds(30)).created()).isTrue();

    assertThat(targets.findActionable(eventId, acceptedCandidate, 100))
        .containsExactly(actionable.betId());
  }
}
