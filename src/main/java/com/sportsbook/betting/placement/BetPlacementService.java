package com.sportsbook.betting.placement;

import com.sportsbook.betting.client.RiskClient;
import com.sportsbook.betting.client.RiskClient.Reservation;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.domain.PlacementPhase;
import com.sportsbook.betting.domain.SystemBetCalculator;
import com.sportsbook.betting.error.BetPlacementException;
import com.sportsbook.betting.error.DuplicateBetException;
import com.sportsbook.betting.error.MarketClosedException;
import com.sportsbook.betting.error.OddsDriftException;
import com.sportsbook.betting.error.RiskLimitException;
import com.sportsbook.betting.error.ValidationFailedException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class BetPlacementService {

  private final BetAssembler assembler;
  private final RiskClient risk;
  private final SystemBetCalculator calculator;
  private final BetStore store;
  private final Clock clock;

  public BetPlacementService(
      BetAssembler assembler,
      RiskClient risk,
      SystemBetCalculator calculator,
      BetStore store,
      Clock clock) {
    this.assembler = assembler;
    this.risk = risk;
    this.calculator = calculator;
    this.store = store;
    this.clock = clock;
  }

  public Bet place(PlaceBetCommand command) {
    String key = command.idempotencyKey().value();
    String fingerprint = RequestFingerprint.of(command);
    Optional<PlacementRequest> request = store.findPlacementRequest(key);
    if (request.isPresent()) {
      return PlacementReplay.request(request.get(), command.userId(), fingerprint, store::findById);
    }
    Optional<Bet> legacyBet = store.findByIdempotencyKey(key);
    if (legacyBet.isPresent()) {
      return PlacementReplay.bet(legacyBet.get(), command.userId(), fingerprint);
    }

    Bet bet;
    try {
      bet = assembler.assemble(command, fingerprint);
    } catch (BetPlacementException rejection) {
      if (!isDurablePreflight(rejection)) {
        throw rejection;
      }
      try {
        store.savePreflightRejection(
            key,
            command.userId(),
            fingerprint,
            rejection.errorCode(),
            rejection.getMessage(),
            clock.instant());
      } catch (DataIntegrityViolationException collision) {
        return replayKnown(key, command, fingerprint, collision);
      }
      throw rejection;
    }

    try {
      store.savePending(bet);
    } catch (DataIntegrityViolationException collision) {
      return replayKnown(key, command, fingerprint, collision);
    }
    return advance(bet.betId(), true);
  }

  private Bet advance(UUID betId, boolean surfaceRejection) {
    Bet current = store.findById(betId);
    if (current.placementPhase() == PlacementPhase.CREATED) {
      reserveRisk(current, surfaceRejection);
      return store.findById(betId);
    }
    return current;
  }

  private void reserveRisk(Bet bet, boolean surfaceRejection) {
    try {
      Reservation reservation =
          risk.reserve(bet.betId(), bet.userId(), totalExposure(bet), selectionIds(bet.legs()));
      store.recordRiskReservation(
          bet.betId(),
          reservation.expiresAt(),
          reservation.token(),
          reservation.alreadyCommitted(),
          clock.instant());
    } catch (RiskLimitException | DuplicateBetException | ValidationFailedException rejection) {
      Bet rejected =
          store.rejectAtCreation(
              bet.betId(), rejection.errorCode(), rejection.getMessage(), clock.instant());
      if (surfaceRejection) {
        throw rejection;
      }
      if (rejected.status() == com.sportsbook.protocol.domain.BetStatus.PENDING) {
        throw new IllegalStateException("Risk rejection was not persisted");
      }
    }
  }

  private com.sportsbook.protocol.value.Money totalExposure(Bet bet) {
    return calculator.totalStake(bet.slipType(), bet.stake(), bet.legs().size());
  }

  private static List<UUID> selectionIds(List<BetLeg> legs) {
    return legs.stream().map(BetLeg::selectionId).toList();
  }

  private Bet replayKnown(
      String key,
      PlaceBetCommand command,
      String fingerprint,
      DataIntegrityViolationException collision) {
    Optional<PlacementRequest> request = store.findPlacementRequest(key);
    if (request.isPresent()) {
      return PlacementReplay.request(request.get(), command.userId(), fingerprint, store::findById);
    }
    Optional<Bet> legacyBet = store.findByIdempotencyKey(key);
    if (legacyBet.isPresent()) {
      return PlacementReplay.bet(legacyBet.get(), command.userId(), fingerprint);
    }
    throw collision;
  }

  private static boolean isDurablePreflight(BetPlacementException rejection) {
    return rejection instanceof ValidationFailedException
        || rejection instanceof MarketClosedException
        || rejection instanceof OddsDriftException;
  }
}
