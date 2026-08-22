package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Money;
import org.junit.jupiter.api.Test;

class EmbeddedMoneyTest {

  @Test
  void roundTripsSharedMoney() {
    Money source = Money.krw(12_500);

    EmbeddedMoney persisted = EmbeddedMoney.of(source);

    assertThat(persisted.amount()).isEqualTo(12_500);
    assertThat(persisted.currency()).isEqualTo(source.currency());
    assertThat(persisted.toMoney()).isEqualTo(source);
  }
}
