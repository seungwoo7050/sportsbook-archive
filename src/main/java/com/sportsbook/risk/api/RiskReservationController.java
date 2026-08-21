package com.sportsbook.risk.api;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.reservation.ReservationDecision;
import com.sportsbook.risk.reservation.ReservationTransition;
import com.sportsbook.risk.reservation.RiskReservationService;
import com.sportsbook.risk.service.RiskCheckCommand;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Betting-owned atomic admission and reservation lifecycle API. */
@RestController
@RequestMapping("/internal/v1/risk/reservations")
public class RiskReservationController {
  public static final String TOKEN_HEADER = "X-Risk-Reservation-Token";

  private final Operations operations;
  private final Clock clock;

  @Autowired
  public RiskReservationController(RiskReservationService service, Clock clock) {
    this(
        new Operations() {
          public ReservationDecision reserve(RiskCheckCommand command) {
            return service.reserve(command);
          }

          public ReservationTransition commit(BetId betId, String token, Instant at) {
            return service.commit(betId, token, at);
          }

          public ReservationTransition release(BetId betId, Instant at) {
            return service.release(betId, at);
          }
        },
        clock);
  }

  RiskReservationController(Operations operations, Clock clock) {
    this.operations = operations;
    this.clock = clock;
  }

  @PostMapping
  public RiskReservationResponse reserve(@Valid @RequestBody RiskCheckRequest request) {
    ReservationDecision decision = operations.reserve(request.toCommand(clock.instant()));
    if (decision.status() == ReservationDecision.Status.CONFLICT) {
      throw RiskApiException.duplicate(request.betId());
    }
    return RiskReservationResponse.from(decision);
  }

  @PutMapping("/{betId}/commit")
  public ResponseEntity<Void> commit(
      @PathVariable UUID betId, @RequestHeader(TOKEN_HEADER) String reservationToken) {
    BetId typedBetId = BetId.of(betId);
    if (!reservationToken.matches("[0-9a-f]{64}")) {
      throw RiskApiException.validation("reservation token must be lowercase SHA-256 hex");
    }
    ReservationTransition result = operations.commit(typedBetId, reservationToken, clock.instant());
    switch (result) {
      case APPLIED, REPLAYED -> {}
      case NOT_FOUND, EXPIRED, TOMBSTONED -> throw RiskApiException.notFound(typedBetId);
      case CONFLICT -> throw RiskApiException.duplicate(typedBetId);
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{betId}")
  public ResponseEntity<Void> release(@PathVariable UUID betId) {
    BetId typedBetId = BetId.of(betId);
    ReservationTransition result = operations.release(typedBetId, clock.instant());
    if (result == ReservationTransition.CONFLICT) {
      throw RiskApiException.committed(typedBetId);
    }
    return ResponseEntity.noContent().build();
  }

  interface Operations {
    ReservationDecision reserve(RiskCheckCommand command);

    ReservationTransition commit(BetId betId, String token, Instant at);

    ReservationTransition release(BetId betId, Instant at);
  }
}
