package com.sportsbook.wallet.domain;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.infrastructure.id.UuidV7;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable journal row. A wallet transfer always persists two matched rows. */
@Entity
@Table(
    name = "ledger_entry",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ledger_entry_idempotency_side",
          columnNames = {"idempotency_key", "side"}),
      @UniqueConstraint(
          name = "uk_ledger_entry_group_side",
          columnNames = {"operation_group_id", "side"})
    },
    indexes = {
      @Index(name = "ix_ledger_entry_account_created", columnList = "account_id, created_at"),
      @Index(name = "ix_ledger_entry_idempotency_key", columnList = "idempotency_key")
    })
public class LedgerEntry {

  @Id
  @Column(name = "entry_id", nullable = false, updatable = false)
  private UUID entryId;

  @Column(name = "account_id", nullable = false, updatable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "bucket", nullable = false, length = 16, updatable = false)
  private BalanceBucket bucket;

  @Enumerated(EnumType.STRING)
  @Column(name = "side", nullable = false, length = 6, updatable = false)
  private LedgerSide side;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "amount", nullable = false, updatable = false)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "currency", nullable = false, length = 3, updatable = false))
  })
  private EmbeddedMoney money;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false, length = 24, updatable = false)
  private LedgerReason reason;

  @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
  private String idempotencyKey;

  @Column(name = "operation_group_id", nullable = false, updatable = false)
  private UUID operationGroupId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected LedgerEntry() {}

  private LedgerEntry(
      UUID entryId,
      TransferLeg leg,
      LedgerSide side,
      Money money,
      LedgerReason reason,
      IdempotencyKey idempotencyKey,
      UUID operationGroupId,
      Instant createdAt) {
    Objects.requireNonNull(money, "money");
    if (!money.isPositive()) {
      throw new IllegalArgumentException("Ledger amount must be strictly positive");
    }
    this.entryId = Objects.requireNonNull(entryId, "entryId");
    this.accountId = Objects.requireNonNull(leg, "leg").accountId();
    this.bucket = leg.bucket();
    this.side = Objects.requireNonNull(side, "side");
    this.money = EmbeddedMoney.of(money);
    this.reason = Objects.requireNonNull(reason, "reason");
    this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey").value();
    this.operationGroupId = Objects.requireNonNull(operationGroupId, "operationGroupId");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static Pair pair(
      TransferLeg destination,
      TransferLeg source,
      Money amount,
      LedgerReason reason,
      IdempotencyKey idempotencyKey,
      UUID operationGroupId,
      Instant now) {
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(source, "source");
    if (destination.equals(source)) {
      throw new IllegalArgumentException("Ledger transfer legs must differ");
    }
    LedgerEntry debit =
        new LedgerEntry(
            UuidV7.generate(),
            destination,
            LedgerSide.DEBIT,
            amount,
            reason,
            idempotencyKey,
            operationGroupId,
            now);
    LedgerEntry credit =
        new LedgerEntry(
            UuidV7.generate(),
            source,
            LedgerSide.CREDIT,
            amount,
            reason,
            idempotencyKey,
            operationGroupId,
            now);
    return new Pair(debit, credit);
  }

  public UUID entryId() {
    return entryId;
  }

  public UUID accountId() {
    return accountId;
  }

  public BalanceBucket bucket() {
    return bucket;
  }

  public LedgerSide side() {
    return side;
  }

  public Money money() {
    return money.toMoney();
  }

  public LedgerReason reason() {
    return reason;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public UUID operationGroupId() {
    return operationGroupId;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public record TransferLeg(UUID accountId, BalanceBucket bucket) {
    public TransferLeg {
      Objects.requireNonNull(accountId, "accountId");
      Objects.requireNonNull(bucket, "bucket");
    }
  }

  public record Pair(LedgerEntry debit, LedgerEntry credit) {
    public Pair {
      Objects.requireNonNull(debit, "debit");
      Objects.requireNonNull(credit, "credit");
    }
  }
}
