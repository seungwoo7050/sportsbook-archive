package com.sportsbook.risk.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sportsbook.risk.reservation.ReservationDecision;
import com.sportsbook.risk.reservation.ReservationState;
import java.time.Instant;
import java.util.List;

/** Admission lease or durable decline returned to betting-service. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RiskReservationResponse(
    boolean approved,
    boolean replayed,
    String rejectionReason,
    List<RiskCheckResponse.PatternFlag> patterns,
    ReservationState reservationState,
    Instant expiresAt,
    String reservationToken) {

  public RiskReservationResponse {
    patterns = List.copyOf(patterns);
  }

  static RiskReservationResponse from(ReservationDecision decision) {
    return new RiskReservationResponse(
        decision.approved(),
        decision.replayed(),
        decision.rejection(),
        decision.patterns().stream().map(RiskCheckResponse.PatternFlag::from).toList(),
        decision.state(),
        decision.expiresAt(),
        decision.token());
  }
}
