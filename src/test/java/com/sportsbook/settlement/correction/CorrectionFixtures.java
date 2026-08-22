package com.sportsbook.settlement.correction;

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

final class CorrectionFixtures {

  private CorrectionFixtures() {}

  static Fixture settledSingle(SettlementResult acceptedOutcome) {
    UUID eventId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    BetSelection selection =
        new BetSelection(eventId, UUID.randomUUID(), selectionId, Odds.ofDecimal("2.0000"));
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
    AcceptedResult accepted =
        new AcceptedResult(
            eventId,
            UUID.randomUUID(),
            MatchOutcomeMode.COMPLETED,
            Map.of(selectionId, acceptedOutcome),
            Instant.EPOCH);
    return new Fixture(bet, accepted);
  }

  record Fixture(Bet bet, AcceptedResult accepted) {}
}
