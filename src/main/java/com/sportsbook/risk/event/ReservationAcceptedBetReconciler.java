package com.sportsbook.risk.event;

import com.sportsbook.risk.reservation.ReservationTransition;
import com.sportsbook.risk.reservation.RiskReservationStore;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Confirms reserved bets and atomically projects first-seen accepted bets. */
@Component
public final class ReservationAcceptedBetReconciler implements AcceptedBetReconciler {
  private final RiskReservationStore reservations;

  public ReservationAcceptedBetReconciler(RiskReservationStore reservations) {
    this.reservations = Objects.requireNonNull(reservations, "reservations");
  }

  @Override
  public AcceptedBetReconciliation reconcile(AcceptedBetEnvelope envelope) {
    Objects.requireNonNull(envelope, "envelope");
    String fingerprint = envelope.reservationFingerprint();
    ReservationTransition transition =
        reservations.commit(envelope.command().betId(), fingerprint, envelope.command().now());
    if (transition == ReservationTransition.NOT_FOUND) {
      transition = reservations.projectAccepted(envelope.command(), fingerprint);
      return switch (transition) {
        case APPLIED -> AcceptedBetReconciliation.PROJECTED;
        case REPLAYED -> AcceptedBetReconciliation.REPLAYED;
        case CONFLICT -> AcceptedBetReconciliation.FINGERPRINT_MISMATCH;
        default -> throw unexpected(transition);
      };
    }
    return switch (transition) {
      case APPLIED -> AcceptedBetReconciliation.CONFIRMED;
      case REPLAYED -> AcceptedBetReconciliation.REPLAYED;
      case CONFLICT -> AcceptedBetReconciliation.FINGERPRINT_MISMATCH;
      case EXPIRED, TOMBSTONED -> AcceptedBetReconciliation.TERMINAL_RESERVATION;
      case NOT_FOUND -> throw unexpected(transition);
    };
  }

  private static IllegalStateException unexpected(ReservationTransition transition) {
    return new IllegalStateException("unexpected accepted projection result: " + transition);
  }
}
