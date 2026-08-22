package com.sportsbook.settlement.readmodel;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.BetSelection;
import com.sportsbook.settlement.domain.EmbeddedMoney;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.persistence.BetRepository;
import com.sportsbook.settlement.result.MatchResultRecord;
import com.sportsbook.settlement.result.MatchResultRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
  private final MatchResultRepository results;
  private final Clock clock;

  public BetReadModelWriter(
      BetRepository repository,
      BetPlacementValidator validator,
      BetPlacementFingerprinter fingerprinter,
      MatchResultRepository results,
      Clock clock) {
    this.repository = repository;
    this.validator = validator;
    this.fingerprinter = fingerprinter;
    this.results = results;
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
    Instant now = clock.instant();
    Bet bet =
        Bet.pending(
            placement.betId(),
            placement.userId(),
            SlipKind.from(placement.slipType()),
            minimumWins,
            totalSelections,
            EmbeddedMoney.of(placement.unitStake()),
            placement.requestedAt(),
            selections,
            now);
    repository.save(bet);
    placement.selections().stream()
        .map(BetPlacement.Selection::eventId)
        .distinct()
        .forEach(eventId -> results.findById(eventId).ifPresent(result -> apply(bet, result, now)));
    return RecordResult.CREATED;
  }

  private static void apply(Bet bet, MatchResultRecord result, Instant now) {
    Map<UUID, SettlementResult> resolved = new LinkedHashMap<>();
    bet.selections().stream()
        .filter(selection -> selection.eventId().equals(result.eventId()))
        .forEach(
            selection ->
                result
                    .mode()
                    .resolve(result.outcomes().get(selection.selectionId()))
                    .ifPresent(outcome -> resolved.put(selection.selectionId(), outcome)));
    bet.applySelectionSnapshot(result.eventId(), resolved, false, now);
  }
}
