package com.sportsbook.oddsfeed.provider;

import com.sportsbook.protocol.event.MatchFinalStatus;
import com.sportsbook.protocol.value.EventId;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record MatchOutcome(
    EventId eventId,
    String score,
    MatchFinalStatus finalStatus,
    Map<String, String> detail,
    Instant settledAt) {

  public MatchOutcome {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(score, "score");
    Objects.requireNonNull(finalStatus, "finalStatus");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(settledAt, "settledAt");
    detail = Map.copyOf(detail);
  }
}
