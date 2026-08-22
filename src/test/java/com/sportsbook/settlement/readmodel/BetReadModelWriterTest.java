package com.sportsbook.settlement.readmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.persistence.BetRepository;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import com.sportsbook.settlement.result.MatchResultRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BetReadModelWriterTest {

  private final BetRepository repository = mock(BetRepository.class);
  private final MatchResultRepository results = mock(MatchResultRepository.class);
  private final BetReadModelWriter writer =
      new BetReadModelWriter(
          repository,
          new BetPlacementValidator(),
          new BetPlacementFingerprinter(),
          results,
          Clock.fixed(Instant.parse("2026-01-02T00:00:00Z"), ZoneOffset.UTC));

  @Test
  void persistsAValidatedPendingSnapshot() {
    BetPlacement placement = placement(100);
    when(repository.findWithSelectionsById(placement.betId())).thenReturn(Optional.empty());

    assertThat(writer.record(placement)).isEqualTo(BetReadModelWriter.RecordResult.CREATED);

    ArgumentCaptor<Bet> saved = ArgumentCaptor.forClass(Bet.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().stake()).isEqualTo(Money.krw(100));
  }

  @Test
  void acceptsOnlyAnExactReplayForAnExistingBetId() {
    BetPlacement original = placement(100);
    Bet stored = stored(original);
    when(repository.findWithSelectionsById(original.betId())).thenReturn(Optional.of(stored));

    assertThat(writer.record(original)).isEqualTo(BetReadModelWriter.RecordResult.EXACT_REPLAY);
    BetPlacement conflict =
        new BetPlacement(
            original.betId(),
            original.userId(),
            original.slipType(),
            Money.krw(101),
            original.requestedAt(),
            original.selections());
    assertThatThrownBy(() -> writer.record(conflict))
        .isInstanceOf(PlacementContractException.class)
        .hasMessageContaining("Conflicting");
    verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void appliesAResultThatArrivedBeforePlacement() {
    BetPlacement placement = placement(100);
    BetPlacement.Selection selection = placement.selections().get(0);
    when(repository.findWithSelectionsById(placement.betId())).thenReturn(Optional.empty());
    when(results.findById(selection.eventId()))
        .thenReturn(
            Optional.of(
                new MatchResultRecord(
                    selection.eventId(),
                    MatchOutcomeMode.VOIDED,
                    Map.of(),
                    Instant.EPOCH,
                    Instant.EPOCH)));

    writer.record(placement);

    ArgumentCaptor<Bet> saved = ArgumentCaptor.forClass(Bet.class);
    verify(repository).save(saved.capture());
    assertThat(saved.getValue().selections().get(0).outcome())
        .isEqualTo(com.sportsbook.protocol.domain.SettlementResult.VOID);
  }

  private static BetPlacement placement(long amount) {
    BetPlacement.Selection selection =
        new BetPlacement.Selection(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    return new BetPlacement(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new BetSlipType.Single(),
        Money.krw(amount),
        Instant.EPOCH,
        List.of(selection));
  }

  private static Bet stored(BetPlacement placement) {
    BetPlacement.Selection selection = placement.selections().get(0);
    BetSelection leg =
        new BetSelection(
            selection.eventId(), selection.marketId(), selection.selectionId(), selection.odds());
    return Bet.pending(
        placement.betId(),
        placement.userId(),
        SlipKind.SINGLE,
        null,
        null,
        EmbeddedMoney.of(placement.unitStake()),
        placement.requestedAt(),
        List.of(leg),
        Instant.EPOCH);
  }
}
