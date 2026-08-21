package com.sportsbook.wallet.domain;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
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
