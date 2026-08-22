package com.sportsbook.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import org.junit.jupiter.api.Test;

class SharedProtocolDependencyTest {

  @Test
  void resolvesTheReleasedMoneyContract() {
    assertThat(Money.krw(1_000)).isEqualTo(new Money(1_000, Currency.KRW));
  }
}
