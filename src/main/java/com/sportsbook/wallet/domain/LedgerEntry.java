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
import jakarta.persistence.Table;
import java.util.UUID;

/** Immutable journal row. A wallet transfer always persists two matched rows. */
@Entity
@Table(name = "ledger_entry")
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
}
