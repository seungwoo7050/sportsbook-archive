package com.sportsbook.wallet.web.dto;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.AdjustmentStatus;
import com.sportsbook.wallet.domain.WalletAdjustment;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public settlement proof without internal retry or persistence metadata. */
public record AdjustmentProofResponse(
    UUID revisionId,
    UUID betId,
    long revisionNumber,
    UUID userId,
    Money previousPayout,
    Money newPayout,
    long deltaAmount,
    Currency currency,
    AdjustmentStatus status,
    Long queueSequence,
    UUID operationGroupId,
    Instant queuedAt,
    Instant appliedAt,
    Instant nextAttemptAt) {

  public static AdjustmentProofResponse from(WalletAdjustment proof) {
    Objects.requireNonNull(proof, "proof");
    return new AdjustmentProofResponse(
        proof.revisionId(),
        proof.betId(),
        proof.revisionNumber(),
        proof.userId(),
        proof.previousPayout(),
        proof.newPayout(),
        proof.deltaAmount(),
        proof.currency(),
        proof.status(),
        proof.queueSequence(),
        proof.operationGroupId(),
        proof.queuedAt(),
        proof.appliedAt(),
        proof.nextAttemptAt());
  }
}
