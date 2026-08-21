package com.sportsbook.risk.api;

import com.sportsbook.risk.reservation.ReservationDecision;
import com.sportsbook.risk.reservation.RiskReservationService;
import com.sportsbook.risk.service.RiskCheckCommand;
import jakarta.validation.Valid;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Betting-owned atomic admission API. */
@RestController
@RequestMapping("/internal/v1/risk/reservations")
public class RiskReservationController {
  private final Operations operations;
  private final Clock clock;

  @Autowired
  public RiskReservationController(RiskReservationService service, Clock clock) {
    this(service::reserve, clock);
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

  @FunctionalInterface
  interface Operations {
    ReservationDecision reserve(RiskCheckCommand command);
  }
}
