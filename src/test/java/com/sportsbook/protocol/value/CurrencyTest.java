package com.sportsbook.protocol.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CurrencyTest {

  @Test
  void exactlyTwoCurrenciesDefinedInV1() {
    assertThat(Currency.values()).containsExactlyInAnyOrder(Currency.KRW, Currency.USD);
  }

  @Test
  void valueOfRoundTripsByName() {
    assertThat(Currency.valueOf("KRW")).isEqualTo(Currency.KRW);
    assertThat(Currency.valueOf("USD")).isEqualTo(Currency.USD);
  }
}
