package com.sportsbook.wallet.domain;

import com.sportsbook.protocol.value.Money;
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
}
