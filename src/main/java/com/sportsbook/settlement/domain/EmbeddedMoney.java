package com.sportsbook.settlement.domain;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/** Persistence mirror that keeps the shared {@link Money} type free of JPA concerns. */
@Embeddable
public class EmbeddedMoney {

  @Column(nullable = false)
  private long amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private Currency currency;

  protected EmbeddedMoney() {}

  public EmbeddedMoney(long amount, Currency currency) {
    this.amount = amount;
    this.currency = Objects.requireNonNull(currency, "currency");
  }

  public static EmbeddedMoney of(Money money) {
    Objects.requireNonNull(money, "money");
    return new EmbeddedMoney(money.amount(), money.currency());
  }

  public Money toMoney() {
    return new Money(amount, currency);
  }

  @Override
  public boolean equals(Object candidate) {
    return this == candidate
        || candidate instanceof EmbeddedMoney other
            && amount == other.amount
            && currency == other.currency;
  }

  @Override
  public int hashCode() {
    return Objects.hash(amount, currency);
  }
}
