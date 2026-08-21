package com.sportsbook.protocol.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class MoneyArithmeticTest {

  @Test
  void factoriesPreserveAmountAndCurrency() {
    assertThat(Money.krw(1_000)).isEqualTo(new Money(1_000, Currency.KRW));
    assertThat(Money.usd(250)).isEqualTo(new Money(250, Currency.USD));
    assertThat(Money.zero(Currency.KRW)).isEqualTo(Money.krw(0));
  }

  @Test
  void arithmeticPreservesCurrency() {
    assertThat(Money.krw(10_000).add(Money.krw(2_500))).isEqualTo(Money.krw(12_500));
    assertThat(Money.krw(10_000).subtract(Money.krw(2_500))).isEqualTo(Money.krw(7_500));
    assertThat(Money.usd(250).multiply(4)).isEqualTo(Money.usd(1_000));
    assertThat(Money.krw(500).negate()).isEqualTo(Money.krw(-500));
  }

  @Test
  void crossCurrencyOperationsAreRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Money.krw(100).add(Money.usd(100)))
        .withMessageContaining("Currency mismatch");
    assertThatIllegalArgumentException().isThrownBy(() -> Money.krw(100).compareTo(Money.usd(100)));
  }

  @Test
  void overflowIsNeverSilent() {
    assertThatExceptionOfType(ArithmeticException.class)
        .isThrownBy(() -> Money.krw(Long.MAX_VALUE).add(Money.krw(1)));
    assertThatExceptionOfType(ArithmeticException.class)
        .isThrownBy(() -> Money.krw(Long.MAX_VALUE).multiply(2));
  }

  @Test
  void comparisonAndSignHelpersReflectAmount() {
    assertThat(Money.krw(100)).isLessThan(Money.krw(200));
    assertThat(Money.krw(0).isZero()).isTrue();
    assertThat(Money.krw(1).isPositive()).isTrue();
    assertThat(Money.krw(-1).isNegative()).isTrue();
  }
}
