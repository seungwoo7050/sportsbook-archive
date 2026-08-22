package com.sportsbook.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import org.junit.jupiter.api.Test;

class EmbeddedMoneyTest {

  @Test
  void roundTripsSharedMoneyWithoutChangingItsSign() {
    Money source = Money.usd(-125);

    assertThat(EmbeddedMoney.of(source).toMoney()).isEqualTo(source);
  }

  @Test
  void comparesAmountAndCurrencyByValue() {
    EmbeddedMoney first = new EmbeddedMoney(500, Currency.KRW);
    EmbeddedMoney same = new EmbeddedMoney(500, Currency.KRW);

    assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
    assertThat(first).isNotEqualTo(new EmbeddedMoney(500, Currency.USD));
  }

  @Test
  void rejectsMissingCurrency() {
    assertThatNullPointerException().isThrownBy(() -> new EmbeddedMoney(1, null));
    assertThatNullPointerException().isThrownBy(() -> EmbeddedMoney.of(null));
  }
}
