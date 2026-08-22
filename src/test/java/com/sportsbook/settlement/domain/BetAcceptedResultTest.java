package com.sportsbook.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetAcceptedResultTest {

  @Test
  void recordsTheCandidateSourceOnlyForResolvedEventSelections() {
    UUID firstEvent = UUID.randomUUID();
    BetSelection first = selection(firstEvent);
    BetSelection unresolved = selection(UUID.randomUUID());
    Bet bet =
        Bet.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SlipKind.MULTIPLE,
            null,
            null,
            EmbeddedMoney.of(Money.krw(100)),
            Instant.EPOCH,
            List.of(first, unresolved),
            Instant.EPOCH);
    UUID candidateId = UUID.randomUUID();

    assertThat(
            bet.applyAcceptedResult(
                firstEvent,
                candidateId,
                Map.of(first.selectionId(), SettlementResult.WON),
                Instant.EPOCH.plusSeconds(1)))
        .isTrue();

    assertThat(first.outcome()).isEqualTo(SettlementResult.WON);
    assertThat(first.sourceCandidateId()).isEqualTo(candidateId);
    assertThat(unresolved.outcome()).isNull();
    assertThat(bet.allSelectionsResolved()).isFalse();
    assertThat(
            bet.applyAcceptedResult(
                firstEvent,
                candidateId,
                Map.of(first.selectionId(), SettlementResult.WON),
                Instant.EPOCH.plusSeconds(2)))
        .isFalse();

    UUID replacement = UUID.randomUUID();
    assertThat(
            bet.applyAcceptedResult(
                firstEvent, replacement, Map.of(), Instant.EPOCH.plusSeconds(3)))
        .isTrue();
    assertThat(first.outcome()).isNull();
    assertThat(first.sourceCandidateId()).isEqualTo(replacement);
  }

  private static BetSelection selection(UUID eventId) {
    return new BetSelection(
        eventId, UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
  }
}
