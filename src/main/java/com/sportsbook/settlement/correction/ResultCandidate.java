package com.sportsbook.settlement.correction;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.infrastructure.id.UuidV7;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ResultCandidate(
    UUID candidateId,
    Long sequence,
    UUID eventId,
    String fingerprint,
    MatchOutcomeMode mode,
    Map<UUID, SettlementResult> outcomes,
    Instant settledAt,
    Instant receivedAt,
    ResultCandidateState state,
    UUID replacesCandidateId,
    Instant decidedAt,
    String decisionReason) {

  public ResultCandidate {
    Objects.requireNonNull(candidateId, "candidateId");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(mode, "mode");
    outcomes = Map.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
    Objects.requireNonNull(settledAt, "settledAt");
    Objects.requireNonNull(receivedAt, "receivedAt");
    Objects.requireNonNull(state, "state");
    if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Candidate fingerprint must be lowercase SHA-256");
    }
    if ((state == ResultCandidateState.PENDING) != (decidedAt == null)) {
      throw new IllegalArgumentException("Candidate decision timestamp must match state");
    }
  }

  public static ResultCandidate pending(
      UUID eventId,
      String fingerprint,
      MatchOutcomeMode mode,
      Map<UUID, SettlementResult> outcomes,
      Instant settledAt,
      Instant receivedAt,
      UUID replacesCandidateId) {
    return new ResultCandidate(
        UuidV7.generate(),
        null,
        eventId,
        fingerprint,
        mode,
        outcomes,
        settledAt,
        receivedAt,
        ResultCandidateState.PENDING,
        replacesCandidateId,
        null,
        null);
  }
}
