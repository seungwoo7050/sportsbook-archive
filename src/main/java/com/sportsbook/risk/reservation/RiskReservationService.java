package com.sportsbook.risk.reservation;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.service.RiskCheckCommand;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Application boundary for atomic betting admission and lifecycle transitions. */
@Service
public final class RiskReservationService {
  private final RiskReservationStore store;
  private final MeterRegistry meters;

  @Autowired
  public RiskReservationService(RiskReservationStore store, MeterRegistry meters) {
    this.store = Objects.requireNonNull(store, "store");
    this.meters = Objects.requireNonNull(meters, "meters");
  }

  RiskReservationService(RiskReservationStore store) {
    this(store, Metrics.globalRegistry);
  }

  public ReservationDecision reserve(RiskCheckCommand command) {
    ReservationDecision decision = store.reserve(Objects.requireNonNull(command, "command"));
    meters.counter("risk.reservation.requests", "result", decisionResult(decision)).increment();
    return decision;
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

  private static String decisionResult(ReservationDecision decision) {
    if (decision.replayed()) {
      return "replayed";
    }
    return switch (decision.status()) {
      case APPROVED -> "created";
      case REJECTED -> "rejected";
      case CONFLICT -> "conflict";
    };
  }
}
