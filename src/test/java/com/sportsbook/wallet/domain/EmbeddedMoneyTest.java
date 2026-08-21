package com.sportsbook.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import org.junit.jupiter.api.Test;

class EmbeddedMoneyTest {

  @Test
  void roundTripsSharedMoneyValues() {
    EmbeddedMoney embedded = EmbeddedMoney.of(Money.krw(37L));

    assertThat(embedded.amount()).isEqualTo(37L);
    assertThat(embedded.currency()).isEqualTo(Currency.KRW);
    assertThat(embedded.toMoney()).isEqualTo(Money.krw(37L));
    assertThat(embedded).isEqualTo(new EmbeddedMoney(37L, Currency.KRW));
    assertThat(embedded.hashCode()).isEqualTo(new EmbeddedMoney(37L, Currency.KRW).hashCode());
    assertThatNullPointerException().isThrownBy(() -> EmbeddedMoney.of(null));
    assertThatNullPointerException().isThrownBy(() -> new EmbeddedMoney(1L, null));
  }
}
