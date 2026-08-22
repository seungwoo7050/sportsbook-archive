package com.sportsbook.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelectionSnapshotTest {

  @Test
  void replacesMatchingEventOutcomesAndClearsMissingSelections() {
    UUID eventId = UUID.randomUUID();
    BetSelection first = selection(eventId);
    BetSelection second = selection(eventId);
    Bet bet = pending(first, second);

    assertThat(
            bet.applySelectionSnapshot(
                eventId,
                Map.of(
                    first.selectionId(), SettlementResult.WON,
                    second.selectionId(), SettlementResult.PUSH),
                true,
                Instant.parse("2026-01-01T00:00:01Z")))
        .isTrue();
    assertThat(bet.allSelectionsResolved()).isTrue();

    assertThat(
            bet.applySelectionSnapshot(
                eventId,
                Map.of(first.selectionId(), SettlementResult.LOST),
                true,
                Instant.parse("2026-01-01T00:00:02Z")))
        .isTrue();
    assertThat(first.outcome()).isEqualTo(SettlementResult.LOST);
    assertThat(second.outcome()).isNull();
    assertThat(bet.allSelectionsResolved()).isFalse();
  }

  private static Bet pending(BetSelection... selections) {
    return Bet.pending(
        UUID.randomUUID(),
        UUID.randomUUID(),
        SlipKind.MULTIPLE,
        null,
        null,
        new EmbeddedMoney(100, Currency.KRW),
        Instant.EPOCH,
        List.of(selections),
        Instant.EPOCH);
  }

  private static BetSelection selection(UUID eventId) {
    return new BetSelection(
        eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
  }
}
