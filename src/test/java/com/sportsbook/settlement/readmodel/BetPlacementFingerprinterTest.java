package com.sportsbook.settlement.readmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetPlacementFingerprinterTest {

  private final BetPlacementFingerprinter fingerprinter = new BetPlacementFingerprinter();

  @Test
  void matchesIncomingAndPersistedSemanticSnapshots() {
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Instant requestedAt = Instant.parse("2026-01-01T00:00:00.123Z");
    BetPlacement.Selection decoded = selection();
    BetPlacement placement =
        new BetPlacement(
            betId, userId, new BetSlipType.Single(), Money.krw(100), requestedAt, List.of(decoded));
    BetSelection stored =
        new BetSelection(
            decoded.eventId(), decoded.marketId(), decoded.selectionId(), decoded.odds());
    Bet bet =
        Bet.pending(
            betId,
            userId,
            SlipKind.SINGLE,
            null,
            null,
            new EmbeddedMoney(100, Currency.KRW),
            requestedAt,
            List.of(stored),
            Instant.EPOCH);

    assertThat(fingerprinter.fingerprint(placement)).isEqualTo(fingerprinter.fingerprint(bet));
    assertThat(fingerprinter.fingerprint(placement)).matches("[0-9a-f]{64}");
  }

  @Test
  void changesWhenReplaySemanticsChange() {
    BetPlacement.Selection selected = selection();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    BetPlacement first =
        new BetPlacement(
            betId,
            userId,
            new BetSlipType.Single(),
            Money.krw(100),
            Instant.EPOCH,
            List.of(selected));
    BetPlacement changed =
        new BetPlacement(
            betId,
            userId,
            new BetSlipType.Single(),
            Money.krw(101),
            Instant.EPOCH,
            List.of(selected));

    assertThat(fingerprinter.fingerprint(first)).isNotEqualTo(fingerprinter.fingerprint(changed));
  }

  private static BetPlacement.Selection selection() {
    return new BetPlacement.Selection(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
  }
}
