package com.sportsbook.risk.reservation;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;

/** Atomic persistence boundary for reservation admission and lifecycle transitions. */
public interface RiskReservationStore {
  ReservationDecision reserve(RiskCheckCommand command);

  ReservationTransition commit(BetId betId, String token, Instant now);

  ReservationTransition projectAccepted(RiskCheckCommand command, String fingerprint);

  ReservationTransition release(BetId betId, Instant now);
}
