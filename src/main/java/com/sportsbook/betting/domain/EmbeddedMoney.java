package com.sportsbook.betting.domain;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

@Embeddable
public class EmbeddedMoney {

  @Column(nullable = false)
  private long amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 3)
  private Currency currency;

  protected EmbeddedMoney() {}

  private EmbeddedMoney(long amount, Currency currency) {
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

  public long amount() {
    return amount;
  }

  public Currency currency() {
    return currency;
  }
}
