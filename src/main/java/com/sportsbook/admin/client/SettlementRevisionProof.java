package com.sportsbook.admin.client;

import java.util.UUID;

public final class SettlementRevisionProof {

  private static final int MAX_ATTEMPTS = 12;

  private SettlementRevisionProof() {}

  public static SettlementRevisionView verify(UUID requestedId, SettlementRevisionView view) {
    if (view == null
        || !requestedId.equals(view.revisionId())
        || view.betId() == null
        || view.revisionNumber() == null
        || view.revisionNumber() < 1
        || view.eventId() == null
        || view.sourceCandidateId() == null
        || view.state() == null
        || view.attemptCount() == null
        || view.attemptCount() < 0
        || view.attemptCount() > MAX_ATTEMPTS
        || view.createdAt() == null
        || view.updatedAt() == null
        || view.updatedAt().isBefore(view.createdAt())
        || (view.state() == SettlementRevisionView.State.APPLIED) != (view.appliedAt() != null)
        || (view.appliedAt() != null && view.appliedAt().isBefore(view.createdAt()))
        || invalidSchedule(view)
        || invalidWalletProof(view)
        || (view.state() == SettlementRevisionView.State.EXHAUSTED
            && (view.lastErrorCode() == null || view.walletStatus() != null))
        || (view.state() == SettlementRevisionView.State.REJECTED
            && view.walletStatus() == null
            && view.lastErrorCode() == null)) {
      throw new DownstreamContractException("complete typed Settlement revision response");
    }
    return view;
  }

  private static boolean invalidSchedule(SettlementRevisionView view) {
    if (view.leaseUntil() != null) {
      return (view.state() != SettlementRevisionView.State.PENDING
              && view.state() != SettlementRevisionView.State.BLOCKED)
          || view.nextRetryAt() != null;
    }
    return switch (view.state()) {
      case PENDING -> view.attemptCount() >= MAX_ATTEMPTS || view.nextRetryAt() == null;
      case BLOCKED ->
          view.nextRetryAt() == null
              ? view.walletStatus() != SettlementRevisionView.WalletStatus.BLOCKED
                  || view.lastErrorCode() == null
              : view.attemptCount() >= MAX_ATTEMPTS;
      case EXHAUSTED, APPLIED, REJECTED -> view.nextRetryAt() != null;
    };
  }

  private static boolean invalidWalletProof(SettlementRevisionView view) {
    boolean queued = view.walletQueueSequence() != null && view.walletQueuedAt() != null;
    if ((view.walletQueueSequence() == null) != (view.walletQueuedAt() == null)
        || (queued && view.walletQueueSequence() <= 0)) {
      return true;
    }
    if (view.walletStatus() == null) {
      return queued
          || view.walletOperationGroupId() != null
          || view.walletAppliedAt() != null
          || view.walletNextAttemptAt() != null;
    }
    return switch (view.walletStatus()) {
      case BLOCKED ->
          (view.state() != SettlementRevisionView.State.PENDING
                  && view.state() != SettlementRevisionView.State.BLOCKED)
              || !queued
              || view.walletOperationGroupId() != null
              || view.walletAppliedAt() != null
              || view.walletNextAttemptAt() == null;
      case APPLIED ->
          view.state() != SettlementRevisionView.State.APPLIED
              || view.walletOperationGroupId() == null
              || view.walletAppliedAt() == null
              || view.walletNextAttemptAt() != null
              || (queued && view.walletAppliedAt().isBefore(view.walletQueuedAt()));
      case REJECTED ->
          view.state() != SettlementRevisionView.State.REJECTED
              || queued
              || view.walletOperationGroupId() != null
              || view.walletAppliedAt() != null
              || view.walletNextAttemptAt() != null;
    };
  }
}
