package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionPlanRowTest {

  @Test
  void rebuildsEveryImmutablePlanField() {
    UUID revisionId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    Instant settledAt = Instant.parse("2026-08-22T00:00:00Z");
    Instant createdAt = settledAt.plusSeconds(1);
    ResolvedSelection selection =
        new ResolvedSelection(UUID.randomUUID(), Odds.ofDecimal("2.2500"), SettlementResult.PUSH);
    ResolvedSelection second =
        new ResolvedSelection(UUID.randomUUID(), Odds.ofDecimal("1.5000"), SettlementResult.WON);
    RevisionPlanRow row =
        new RevisionPlanRow(
            revisionId,
            betId,
            3,
            userId,
            eventId,
            candidateId,
            SettlementResult.WON,
            450,
            SettlementResult.PUSH,
            100,
            Currency.KRW,
            SlipKind.SYSTEM,
            1,
            2,
            100,
            settledAt,
            createdAt);

    RevisionPlan plan = row.toPlan(List.of(selection, second));

    assertThat(plan.revisionId()).isEqualTo(revisionId);
    assertThat(plan.createdAt()).isEqualTo(createdAt);
    assertThat(plan.newResult()).isEqualTo(SettlementResult.PUSH);
    assertThat(plan.newPayout()).isEqualTo(Money.krw(100));
    assertThat(plan.target().betId()).isEqualTo(betId);
    assertThat(plan.target().revisionNumber()).isEqualTo(3);
    assertThat(plan.target().userId()).isEqualTo(userId);
    assertThat(plan.target().eventId()).isEqualTo(eventId);
    assertThat(plan.target().sourceCandidateId()).isEqualTo(candidateId);
    assertThat(plan.target().previousResult()).isEqualTo(SettlementResult.WON);
    assertThat(plan.target().previousPayout()).isEqualTo(Money.krw(450));
    assertThat(plan.target().slipType()).isEqualTo(new BetSlipType.System(1, 2));
    assertThat(plan.target().unitStake()).isEqualTo(Money.krw(100));
    assertThat(plan.target().selections()).containsExactly(selection, second);
    assertThat(plan.target().sourceResultSettledAt()).isEqualTo(settledAt);
  }
}
