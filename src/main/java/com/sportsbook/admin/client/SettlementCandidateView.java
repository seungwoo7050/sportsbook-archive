package com.sportsbook.admin.client;

import java.time.Instant;
import java.util.UUID;

public record SettlementCandidateView(
    UUID candidateId,
    UUID eventId,
    Mode mode,
    Instant settledAt,
    Instant receivedAt,
    State state,
    UUID replacesCandidateId,
    String decisionReason,
    Instant decidedAt,
    Boolean accepted) {

  public static SettlementCandidateView verify(UUID requestedId, SettlementCandidateView view) {
    if (view == null
        || !requestedId.equals(view.candidateId())
        || view.eventId() == null
        || view.mode() == null
        || view.settledAt() == null
        || view.receivedAt() == null
        || view.state() == null
        || view.accepted() == null
        || view.accepted() != (view.state() == State.ACCEPTED)
        || (view.state() == State.PENDING) != (view.decidedAt() == null)) {
      throw new DownstreamContractException("complete typed Settlement candidate response");
    }
    return view;
  }

  public enum Mode {
    COMPLETED,
    ABANDONED,
    VOIDED
  }

  public enum State {
    PENDING,
    ACCEPTED,
    SUPERSEDED,
    REJECTED
  }
}
