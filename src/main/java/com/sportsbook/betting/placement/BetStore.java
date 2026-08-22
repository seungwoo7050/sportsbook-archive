package com.sportsbook.betting.placement;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.betting.persistence.PlacementRequestRepository;
import com.sportsbook.protocol.error.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BetStore {

  private final BetRepository bets;
  private final PlacementRequestRepository requests;

  public BetStore(BetRepository bets, PlacementRequestRepository requests) {
    this.bets = bets;
    this.requests = requests;
  }

  @Transactional(readOnly = true)
  public Optional<PlacementRequest> findPlacementRequest(String idempotencyKey) {
    return requests.findById(idempotencyKey);
  }

  @Transactional(readOnly = true)
  public Optional<Bet> findByIdempotencyKey(String idempotencyKey) {
    return bets.findByIdempotencyKey(idempotencyKey);
  }

  @Transactional(readOnly = true)
  public Bet findById(UUID betId) {
    return bets.findWithLegsByBetId(betId)
        .orElseThrow(() -> new IllegalStateException("Bet not found during placement: " + betId));
  }

  @Transactional
  public void savePending(Bet bet) {
    bets.saveAndFlush(bet);
    requests.saveAndFlush(PlacementRequest.forBet(bet));
  }

  @Transactional
  public void savePreflightRejection(
      String key, UUID userId, String fingerprint, ErrorCode code, String detail, Instant now) {
    requests.saveAndFlush(PlacementRequest.rejected(key, userId, fingerprint, code, detail, now));
  }
}
