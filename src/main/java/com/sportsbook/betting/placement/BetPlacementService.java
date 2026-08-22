package com.sportsbook.betting.placement;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.error.BetPlacementException;
import com.sportsbook.betting.error.MarketClosedException;
import com.sportsbook.betting.error.OddsDriftException;
import com.sportsbook.betting.error.ValidationFailedException;
import java.time.Clock;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class BetPlacementService {

  private final BetAssembler assembler;
  private final BetStore store;
  private final Clock clock;

  public BetPlacementService(BetAssembler assembler, BetStore store, Clock clock) {
    this.assembler = assembler;
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
    return bet;
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
