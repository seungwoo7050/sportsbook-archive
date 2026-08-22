package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.correction.RevisionPlan;
import com.sportsbook.settlement.correction.RevisionPlanReader;
import com.sportsbook.settlement.correction.RevisionPlanRepository;
import com.sportsbook.settlement.correction.RevisionTarget;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresRevisionPlanReadIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private RevisionPlanRepository plans;
  @Autowired private RevisionPlanReader reader;

  @Test
  void roundTripsTheExactImmutablePlanAndOrderedSelections() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    UUID candidateId = UUID.randomUUID();
    Instant settledAt = Instant.parse("2026-08-22T00:00:00Z");
    Instant createdAt = settledAt.plusSeconds(1);
    jdbc.update(
        "insert into result_candidate (candidate_id,event_id,fingerprint,mode,settled_at,"
            + "received_at,state,decided_at) values (?,?,?,'COMPLETED',?,?,"
            + "'ACCEPTED',?)",
        candidateId,
        bet.eventId(),
        candidateId.toString().replace("-", "").repeat(2),
        Timestamp.from(settledAt),
        Timestamp.from(settledAt),
        Timestamp.from(settledAt));
    jdbc.update(
        "update bet set status='SETTLED',result='WON',payout_amount=200,"
            + "payout_currency='KRW',settled_at=? where bet_id=?",
        Timestamp.from(settledAt),
        bet.betId());
    RevisionTarget target =
        new RevisionTarget(
            bet.betId(),
            1,
            bet.userId(),
            bet.eventId(),
            candidateId,
            SettlementResult.WON,
            Money.krw(200),
            new BetSlipType.Single(),
            Money.krw(100),
            List.of(
                new ResolvedSelection(
                    bet.selectionId(), Odds.ofDecimal("2.0000"), SettlementResult.PUSH)),
            settledAt);
    RevisionPlan plan =
        new RevisionPlan(
            UUID.randomUUID(), target, SettlementResult.PUSH, Money.krw(100), createdAt);

    var persisted = plans.persist(plan, Duration.ofSeconds(30));

    assertThat(persisted.created()).isTrue();
    assertThat(reader.find(plan.revisionId())).contains(persisted.durablePlan(plan));
  }
}
