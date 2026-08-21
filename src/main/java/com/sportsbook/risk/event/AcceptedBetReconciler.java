package com.sportsbook.risk.event;

/** Atomic boundary for reservation confirmation or first-seen accepted-bet projection. */
@FunctionalInterface
public interface AcceptedBetReconciler {
  AcceptedBetReconciliation reconcile(AcceptedBetEnvelope envelope);
}
