package com.sportsbook.settlement.result;

import com.sportsbook.protocol.domain.SettlementResult;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable projection of the currently accepted result evidence for one event. */
public record AcceptedResult(
    UUID eventId,
    UUID candidateId,
    MatchOutcomeMode mode,
    Map<UUID, SettlementResult> outcomes,
    Instant sourceSettledAt) {

  public AcceptedResult {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(candidateId, "candidateId");
    Objects.requireNonNull(mode, "mode");
    LinkedHashMap<UUID, SettlementResult> ordered = new LinkedHashMap<>();
    Objects.requireNonNull(outcomes, "outcomes")
        .forEach(
            (selectionId, outcome) ->
                ordered.put(
                    Objects.requireNonNull(selectionId, "selectionId"),
                    Objects.requireNonNull(outcome, "outcome")));
    outcomes = Collections.unmodifiableMap(ordered);
    Objects.requireNonNull(sourceSettledAt, "sourceSettledAt");
  }

  public Optional<SettlementResult> resolve(UUID selectionId) {
    return mode.resolve(outcomes.get(Objects.requireNonNull(selectionId, "selectionId")));
  }
}
