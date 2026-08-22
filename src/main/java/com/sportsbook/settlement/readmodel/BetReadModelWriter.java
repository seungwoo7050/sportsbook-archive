package com.sportsbook.settlement.readmodel;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.persistence.BetRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists an immutable placement snapshot and isolates conflicting replays. */
@Service
public class BetReadModelWriter {

  public enum RecordResult {
    CREATED,
    EXACT_REPLAY
  }

  private final BetRepository repository;
  private final BetPlacementValidator validator;
  private final BetPlacementFingerprinter fingerprinter;
  private final Clock clock;

  public BetReadModelWriter(
      BetRepository repository,
      BetPlacementValidator validator,
      BetPlacementFingerprinter fingerprinter,
      Clock clock) {
    this.repository = repository;
    this.validator = validator;
    this.fingerprinter = fingerprinter;
    this.clock = clock;
  }

  @Transactional
  public RecordResult record(BetPlacement candidate) {
    BetPlacement placement = validator.validate(candidate);
    return repository
        .findWithSelectionsById(placement.betId())
        .map(existing -> replay(existing, placement))
        .orElseGet(() -> create(placement));
  }

  private RecordResult replay(Bet existing, BetPlacement placement) {
    if (!fingerprinter.fingerprint(existing).equals(fingerprinter.fingerprint(placement))) {
      throw new PlacementContractException("Conflicting BetPlacedRequested replay");
    }
    return RecordResult.EXACT_REPLAY;
  }

  private RecordResult create(BetPlacement placement) {
    List<BetSelection> selections =
        placement.selections().stream()
            .map(
                selection ->
                    new BetSelection(
                        selection.eventId(),
                        selection.marketId(),
                        selection.selectionId(),
                        selection.odds()))
            .toList();
    Integer minimumWins = null;
    Integer totalSelections = null;
    if (placement.slipType() instanceof BetSlipType.System system) {
      minimumWins = system.minWins();
      totalSelections = system.totalSelections();
    }
    repository.save(
        Bet.pending(
            placement.betId(),
            placement.userId(),
            SlipKind.from(placement.slipType()),
            minimumWins,
            totalSelections,
            EmbeddedMoney.of(placement.unitStake()),
            placement.requestedAt(),
            selections,
            clock.instant()));
    return RecordResult.CREATED;
  }
}
