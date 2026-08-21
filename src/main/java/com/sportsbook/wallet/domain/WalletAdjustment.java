package com.sportsbook.wallet.domain;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable request identity and proof for one nonzero settlement payout correction. */
@Entity
@Table(name = "wallet_adjustment")
public class WalletAdjustment {
  @Id
  @Column(name = "revision_id", nullable = false, updatable = false)
  private UUID revisionId;

  @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
  private String idempotencyKey;

  @Column(name = "bet_id", nullable = false, updatable = false)
  private UUID betId;

  @Column(name = "revision_number", nullable = false, updatable = false)
  private long revisionNumber;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "previous_payout_amount", nullable = false, updatable = false)
  private long previousPayoutAmount;

  @Column(name = "new_payout_amount", nullable = false, updatable = false)
  private long newPayoutAmount;

  @Column(name = "delta_amount", nullable = false, updatable = false)
  private long deltaAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false, length = 3, updatable = false)
  private Currency currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AdjustmentStatus status;

  @Column(name = "queue_sequence")
  private Long queueSequence;

  @Column(name = "operation_group_id")
  private UUID operationGroupId;

  @Column(name = "queued_at")
  private Instant queuedAt;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected WalletAdjustment() {}

  private WalletAdjustment(AdjustmentCommand command, Instant now) {
    Objects.requireNonNull(command, "command");
    this.revisionId = command.revisionId();
    this.idempotencyKey = command.idempotencyKey().value();
    this.betId = command.betId();
    this.revisionNumber = command.revisionNumber();
    this.userId = command.userId();
    this.previousPayoutAmount = command.previousPayout().amount();
    this.newPayoutAmount = command.newPayout().amount();
    this.deltaAmount = command.deltaAmount();
    this.currency = command.previousPayout().currency();
    this.createdAt = Objects.requireNonNull(now, "now");
    this.updatedAt = now;
  }

  public static WalletAdjustment applied(
      AdjustmentCommand command, UUID operationGroupId, Instant now) {
    WalletAdjustment proof = new WalletAdjustment(command, now);
    proof.status = AdjustmentStatus.APPLIED;
    proof.operationGroupId = Objects.requireNonNull(operationGroupId, "operationGroupId");
    proof.appliedAt = now;
    return proof;
  }

  public static WalletAdjustment blocked(
      AdjustmentCommand command, long queueSequence, Instant now) {
    if (command.deltaAmount() >= 0L) {
      throw new IllegalArgumentException("Only negative adjustments can be blocked");
    }
    if (queueSequence < 1L) {
      throw new IllegalArgumentException("Queue sequence must be positive");
    }
    WalletAdjustment proof = new WalletAdjustment(command, now);
    proof.status = AdjustmentStatus.BLOCKED;
    proof.queueSequence = queueSequence;
    proof.queuedAt = now;
    proof.nextAttemptAt = now;
    return proof;
  }

  public static WalletAdjustment rejected(AdjustmentCommand command, Instant now) {
    WalletAdjustment proof = new WalletAdjustment(command, now);
    proof.status = AdjustmentStatus.REJECTED;
    return proof;
  }

  public void wake(Instant now) {
    if (status != AdjustmentStatus.BLOCKED) {
      throw new IllegalStateException("Only blocked adjustments can be woken");
    }
    Objects.requireNonNull(now, "now");
    if (now.isBefore(nextAttemptAt)) {
      nextAttemptAt = now;
    }
    updatedAt = now;
  }

  public void deferUntil(Instant attemptedAt, Instant retryAt) {
    requireBlocked();
    Objects.requireNonNull(attemptedAt, "attemptedAt");
    Objects.requireNonNull(retryAt, "retryAt");
    if (retryAt.isBefore(attemptedAt)) {
      throw new IllegalArgumentException("Recovery retry timestamps are out of order");
    }
    retryCount = Math.incrementExact(retryCount);
    nextAttemptAt = retryAt;
    updatedAt = attemptedAt;
  }

  public void completeRecovery(UUID groupId, Instant now) {
    requireBlocked();
    Objects.requireNonNull(now, "now");
    UUID completedGroupId = Objects.requireNonNull(groupId, "groupId");
    status = AdjustmentStatus.APPLIED;
    operationGroupId = completedGroupId;
    appliedAt = now;
    nextAttemptAt = null;
    updatedAt = now;
  }

  private void requireBlocked() {
    if (status != AdjustmentStatus.BLOCKED) {
      throw new IllegalStateException("Only blocked adjustments can change recovery state");
    }
  }

  public UUID revisionId() {
    return revisionId;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public UUID betId() {
    return betId;
  }

  public long revisionNumber() {
    return revisionNumber;
  }

  public UUID userId() {
    return userId;
  }

  public Money previousPayout() {
    return new Money(previousPayoutAmount, currency);
  }

  public Money newPayout() {
    return new Money(newPayoutAmount, currency);
  }

  public long deltaAmount() {
    return deltaAmount;
  }

  public Currency currency() {
    return currency;
  }

  public AdjustmentStatus status() {
    return status;
  }

  public Long queueSequence() {
    return queueSequence;
  }

  public UUID operationGroupId() {
    return operationGroupId;
  }

  public Instant queuedAt() {
    return queuedAt;
  }

  public Instant appliedAt() {
    return appliedAt;
  }

  public Instant nextAttemptAt() {
    return nextAttemptAt;
  }

  public int retryCount() {
    return retryCount;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
