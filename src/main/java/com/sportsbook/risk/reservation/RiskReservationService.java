package com.sportsbook.risk.reservation;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Application boundary for atomic betting admission and lifecycle transitions. */
@Service
public final class RiskReservationService {
  private final RiskReservationStore store;

  public RiskReservationService(RiskReservationStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  public ReservationDecision reserve(RiskCheckCommand command) {
    return store.reserve(Objects.requireNonNull(command, "command"));
  }

  public ReservationTransition commit(BetId betId, String token, Instant now) {
    return store.commit(
        Objects.requireNonNull(betId, "betId"),
        Objects.requireNonNull(token, "token"),
        Objects.requireNonNull(now, "now"));
  }

  public ReservationTransition release(BetId betId, Instant now) {
    return store.release(
        Objects.requireNonNull(betId, "betId"), Objects.requireNonNull(now, "now"));
  }
}
