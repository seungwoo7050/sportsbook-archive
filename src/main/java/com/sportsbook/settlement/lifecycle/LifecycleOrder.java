package com.sportsbook.settlement.lifecycle;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class LifecycleOrder {

  private static final Comparator<LifecycleObservation> CAUSAL =
      Comparator.comparing(LifecycleObservation::occurredAt)
          .thenComparingInt(observation -> rank(observation.status()))
          .thenComparing(LifecycleObservation::fingerprint);

  public Optional<LifecycleObservation> latest(List<LifecycleObservation> observations) {
    return observations.stream().max(CAUSAL);
  }

  private static int rank(EventLifecycleStatus status) {
    return switch (status) {
      case SCHEDULED -> 0;
      case IN_PLAY -> 1;
      case FINISHED -> 2;
      case POSTPONED -> 3;
      case CANCELLED -> 4;
    };
  }
}
