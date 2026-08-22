package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.result.AcceptedResult;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CorrectionMultipleSnapshotTest {

  @Test
  void replacesOnlyTheCorrectedEventInAMultipleSnapshot() {
    UUID correctedEvent = UUID.randomUUID();
    UUID retainedEvent = UUID.randomUUID();
    BetSelection corrected = selection(correctedEvent, SettlementResult.LOST);
    BetSelection retained = selection(retainedEvent, SettlementResult.WON);
    Bet bet =
        Bet.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SlipKind.MULTIPLE,
            null,
            null,
            EmbeddedMoney.of(Money.krw(100)),
            Instant.EPOCH,
            List.of(corrected, retained),
            Instant.EPOCH);
    bet.recordSettled(SettlementResult.LOST, Money.krw(0), Instant.EPOCH);
    UUID replacement = UUID.randomUUID();
    AcceptedResult accepted =
        new AcceptedResult(
            correctedEvent,
            replacement,
            MatchOutcomeMode.COMPLETED,
            Map.of(corrected.selectionId(), SettlementResult.WON),
            Instant.EPOCH.plusSeconds(1));

    RevisionTarget target = new ReplacementSnapshotProjector().project(bet, accepted).orElseThrow();

    assertThat(target.sourceCandidateId()).isEqualTo(replacement);
    assertThat(target.selections())
        .extracting(selection -> selection.outcome())
        .containsExactly(SettlementResult.WON, SettlementResult.WON);
    assertThat(new RevisionResolver().resolve(target).payout()).isEqualTo(Money.krw(400));
  }

  private static BetSelection selection(UUID eventId, SettlementResult outcome) {
    BetSelection selection =
        new BetSelection(eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    selection.applyCandidate(UUID.randomUUID(), outcome);
    return selection;
  }
}
