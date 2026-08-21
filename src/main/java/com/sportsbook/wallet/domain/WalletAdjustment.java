package com.sportsbook.wallet.domain;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}
