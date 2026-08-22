package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplacementSnapshotProjectorTest {

  private final ReplacementSnapshotProjector projector = new ReplacementSnapshotProjector();

  @Test
  void appliesModeRulesWithoutMutatingTheTerminalBet() {
    Fixture fixture = settledFixture();

    assertThat(projector.project(fixture.bet(), candidate(fixture, MatchOutcomeMode.COMPLETED)))
        .isEmpty();
    assertThat(outcome(fixture, MatchOutcomeMode.ABANDONED)).isEqualTo(SettlementResult.VOID);
    assertThat(outcome(fixture, MatchOutcomeMode.VOIDED)).isEqualTo(SettlementResult.VOID);
    assertThat(fixture.bet().selections().get(0).outcome()).isEqualTo(SettlementResult.WON);
  }

  private SettlementResult outcome(Fixture fixture, MatchOutcomeMode mode) {
    return projector
        .project(fixture.bet(), candidate(fixture, mode))
        .orElseThrow()
        .selections()
        .get(0)
        .outcome();
  }

  private static ResultCandidate candidate(Fixture fixture, MatchOutcomeMode mode) {
    return new ResultCandidate(
        UUID.randomUUID(),
        2L,
        fixture.eventId(),
        "a".repeat(64),
        mode,
        Map.of(),
        Instant.EPOCH.plusSeconds(1),
        Instant.EPOCH.plusSeconds(2),
        ResultCandidateState.ACCEPTED,
        UUID.randomUUID(),
        Instant.EPOCH.plusSeconds(2),
        "TEST");
  }

  private static Fixture settledFixture() {
    UUID eventId = UUID.randomUUID();
    BetSelection selection =
        new BetSelection(eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    selection.applyCandidate(UUID.randomUUID(), SettlementResult.WON);
    Bet bet =
        Bet.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SlipKind.SINGLE,
            null,
            null,
            EmbeddedMoney.of(Money.krw(100)),
            Instant.EPOCH,
            List.of(selection),
            Instant.EPOCH);
    bet.recordSettled(SettlementResult.WON, Money.krw(200), Instant.EPOCH);
    return new Fixture(bet, eventId);
  }

  private record Fixture(Bet bet, UUID eventId) {}
}
