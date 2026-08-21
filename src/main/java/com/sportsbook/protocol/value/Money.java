package com.sportsbook.protocol.value;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import java.util.Objects;

/**
 * Money as {@code long} minor units + a {@link Currency} discriminator (ADR-0003).
 *
 * <p>Negative amounts are allowed because ledger entries (debit/credit) need both signs;
 * domain-level "balance must be non-negative" rules live in wallet-service, not here. All
 * arithmetic uses {@link Math#addExact} / {@link Math#multiplyExact} / {@link Math#subtractExact} /
 * {@link Math#negateExact} so silent overflow is impossible — an overflowing operation throws
 * {@link ArithmeticException}.
 *
 * <p>{@code @JsonAutoDetect(isGetterVisibility = NONE)}: Jackson otherwise treats {@code isZero()}
 * / {@code isPositive()} / {@code isNegative()} as boolean bean properties and leaks them into the
 * JSON payload alongside the record components. Disabling is-getter discovery limits the wire shape
 * to {@code amount} + {@code currency}.
 */
@JsonAutoDetect(isGetterVisibility = Visibility.NONE)
public record Money(long amount, Currency currency) implements Comparable<Money> {

  public Money {
    Objects.requireNonNull(currency, "currency");
  }

  public Money add(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(amount, other.amount), currency);
  }

  public Money subtract(Money other) {
    requireSameCurrency(other);
    return new Money(Math.subtractExact(amount, other.amount), currency);
  }

  public Money multiply(long multiplier) {
    return new Money(Math.multiplyExact(amount, multiplier), currency);
  }

  public Money negate() {
    return new Money(Math.negateExact(amount), currency);
  }

  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return Long.compare(amount, other.amount);
  }

  public boolean isZero() {
    return amount == 0L;
  }

  public boolean isPositive() {
    return amount > 0L;
  }

  public boolean isNegative() {
    return amount < 0L;
  }

  private void requireSameCurrency(Money other) {
    if (currency != other.currency) {
      throw new IllegalArgumentException(
          "Currency mismatch: " + currency + " vs " + other.currency);
    }
  }

  public static Money krw(long amount) {
    return new Money(amount, Currency.KRW);
  }

  public static Money usd(long amount) {
    return new Money(amount, Currency.USD);
  }

  public static Money zero(Currency currency) {
    return new Money(0L, currency);
  }
}
