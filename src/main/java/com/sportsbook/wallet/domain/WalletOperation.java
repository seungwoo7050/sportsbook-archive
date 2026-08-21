package com.sportsbook.wallet.domain;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Authoritative immutable request identity and durable outcome for one idempotency key. */
@Entity
@Table(name = "wallet_operation")
public class WalletOperation {
  @Id
  @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "caller_id", nullable = false, length = 16, updatable = false)
  private WalletCaller caller;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation_kind", nullable = false, length = 32, updatable = false)
  private WalletOperationKind kind;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "request_amount", nullable = false, updatable = false)),
    @AttributeOverride(
        name = "currency",
        column =
            @Column(name = "request_currency", nullable = false, length = 3, updatable = false))
  })
  private EmbeddedMoney requestAmount;

  @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
  private String requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 24)
  private WalletOperationStatus status;

  @Column(name = "operation_group_id")
  private UUID operationGroupId;

  @Embedded private WalletFailureSnapshot failure;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Version
  @Column(nullable = false)
  private long version;

  protected WalletOperation() {}

  private WalletOperation(
      IdempotencyKey key,
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount,
      String fingerprint,
      Instant now) {
    if (!amount.isPositive()) {
      throw new IllegalArgumentException("Operation amount must be strictly positive");
    }
    Objects.requireNonNull(caller, "caller");
    Objects.requireNonNull(kind, "kind");
    this.idempotencyKey = Objects.requireNonNull(key, "key").value();
    this.caller = caller;
    this.kind = kind;
    this.userId = Objects.requireNonNull(userId, "userId");
    this.requestAmount = EmbeddedMoney.of(amount);
    this.requestFingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    this.requestedAt = Objects.requireNonNull(now, "now");
    this.updatedAt = now;
  }

  public static WalletOperation succeeded(
      IdempotencyKey key,
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount,
      String fingerprint,
      UUID operationGroupId,
      Instant now) {
    WalletOperation operation =
        new WalletOperation(key, caller, kind, userId, amount, fingerprint, now);
    operation.status = WalletOperationStatus.SUCCEEDED;
    operation.operationGroupId = Objects.requireNonNull(operationGroupId, "operationGroupId");
    operation.completedAt = now;
    return operation;
  }

  public static WalletOperation rejected(
      IdempotencyKey key,
      WalletCaller caller,
      WalletOperationKind kind,
      UUID userId,
      Money amount,
      String fingerprint,
      WalletFailureSnapshot failure,
      Instant now) {
    WalletOperation operation =
        new WalletOperation(key, caller, kind, userId, amount, fingerprint, now);
    operation.status = WalletOperationStatus.REJECTED;
    operation.failure = Objects.requireNonNull(failure, "failure");
    operation.completedAt = now;
    return operation;
  }

  public static WalletOperation blockedFunds(
      IdempotencyKey key,
      WalletCaller caller,
      UUID userId,
      Money amount,
      String fingerprint,
      Instant now) {
    WalletOperation operation =
        new WalletOperation(
            key, caller, WalletOperationKind.BET_ADJUSTMENT, userId, amount, fingerprint, now);
    operation.status = WalletOperationStatus.BLOCKED_FUNDS;
    return operation;
  }

  public WalletOperationStatus status() {
    return status;
  }

  public UUID operationGroupId() {
    return operationGroupId;
  }

  public WalletFailureSnapshot failure() {
    return failure;
  }

  public Instant requestedAt() {
    return requestedAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Instant completedAt() {
    return completedAt;
  }
}
