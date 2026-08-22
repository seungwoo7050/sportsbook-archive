package com.sportsbook.settlement.event;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.MatchResult;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MatchResultMapper {

  public MatchResultRecord map(MatchResult event, Instant receivedAt) {
    Objects.requireNonNull(event, "event");
    MatchOutcomeMode mode =
        switch (Objects.requireNonNull(event.getFinalStatus(), "finalStatus")) {
          case COMPLETED -> MatchOutcomeMode.COMPLETED;
          case ABANDONED -> MatchOutcomeMode.ABANDONED;
          case VOIDED -> MatchOutcomeMode.VOIDED;
        };
    Map<UUID, SettlementResult> outcomes = new LinkedHashMap<>();
    Objects.requireNonNull(event.getResultDetail(), "resultDetail")
        .forEach(
            (selection, outcome) -> {
              UUID selectionId = canonicalUuid(selection, "selectionId");
              SettlementResult parsed =
                  SettlementResult.valueOf(Objects.requireNonNull(outcome, "outcome").toString());
              if (outcomes.putIfAbsent(selectionId, parsed) != null) {
                throw new IllegalArgumentException("duplicate selectionId");
              }
            });
    return new MatchResultRecord(
        canonicalUuid(event.getEventId(), "eventId"),
        mode,
        outcomes,
        Objects.requireNonNull(event.getSettledAt(), "settledAt"),
        Objects.requireNonNull(receivedAt, "receivedAt"));
  }

  private static UUID canonicalUuid(CharSequence encoded, String field) {
    if (encoded == null) {
      throw new IllegalArgumentException(field + " is required");
    }
    String text = encoded.toString();
    UUID value = UUID.fromString(text);
    if (!value.toString().equals(text)) {
      throw new IllegalArgumentException(field + " must be canonical lowercase UUID text");
    }
    return value;
  }
}
