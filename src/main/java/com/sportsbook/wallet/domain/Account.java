package com.sportsbook.wallet.domain;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.error.BalanceLimitExceededException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "account")
public class Account {
  @Id
  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(
        name = "amount",
        column = @Column(name = "available_amount", nullable = false)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "available_currency", nullable = false, length = 3))
  })
  private EmbeddedMoney available;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "amount", column = @Column(name = "locked_amount", nullable = false)),
    @AttributeOverride(
        name = "currency",
        column = @Column(name = "locked_currency", nullable = false, length = 3))
  })
  private EmbeddedMoney locked;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "recovery_debt_amount", nullable = false, precision = 38, scale = 0)
  private BigInteger recoveryDebtAmount;

  @Column(name = "recovery_frozen_at")
  private Instant recoveryFrozenAt;

  @Column(name = "next_adjustment_sequence", nullable = false)
  private long nextAdjustmentSequence;

  protected Account() {}

  private Account(UUID userId, Currency currency, Instant now) {
    this.userId = Objects.requireNonNull(userId, "userId");
    if (SystemAccountIds.isSystemAccount(userId)) {
      throw new IllegalArgumentException("System UUID cannot own an account: " + userId);
    }
    Objects.requireNonNull(currency, "currency");
    this.available = new EmbeddedMoney(0L, currency);
    this.locked = new EmbeddedMoney(0L, currency);
    this.recoveryDebtAmount = BigInteger.ZERO;
    this.nextAdjustmentSequence = 1L;
    this.createdAt = Objects.requireNonNull(now, "now");
    this.updatedAt = now;
  }

  public static Account openFor(UUID userId, Currency currency, Instant now) {
    return new Account(userId, currency, now);
  }

  public UUID userId() {
    return userId;
  }

  public Currency currency() {
    return available.currency();
  }

  public Money available() {
    return available.toMoney();
  }

  public Money locked() {
    return locked.toMoney();
  }

  public Money total() {
    requireRepresentableBalance(userId, available.amount(), locked.amount());
    return new Money(available.amount() + locked.amount(), currency());
  }

  public long version() {
    return version;
  }

  public BigInteger recoveryDebtAmount() {
    return recoveryDebtAmount;
  }

  public Instant recoveryFrozenAt() {
    return recoveryFrozenAt;
  }

  public long nextAdjustmentSequence() {
    return nextAdjustmentSequence;
  }

  public Instant createdAt() {
    return createdAt;
  }

  static void requireRepresentableBalance(UUID userId, long availableAmount, long lockedAmount) {
    if (availableAmount < 0L || lockedAmount < 0L) {
      throw new IllegalArgumentException("Balance buckets cannot be negative");
    }
    if (availableAmount > Long.MAX_VALUE - lockedAmount) {
      throw new BalanceLimitExceededException(userId, availableAmount, lockedAmount);
    }
  }
}
