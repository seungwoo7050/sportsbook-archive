package com.sportsbook.risk.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SafeRedisNumberTest {
  @Test
  void acceptsTheLargestExactlyRepresentableInteger() {
    assertThat(SafeRedisNumber.requireNonNegative(SafeRedisNumber.MAX_VALUE, "amount"))
        .isEqualTo(SafeRedisNumber.MAX_VALUE);
  }

  @Test
  void rejectsNegativeAndInexactValues() {
    assertThatThrownBy(() -> SafeRedisNumber.requireNonNegative(-1L, "amount"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> SafeRedisNumber.requireNonNegative(SafeRedisNumber.MAX_VALUE + 1L, "amount"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void checksAdditionAndMultiplicationResults() {
    assertThat(SafeRedisNumber.add(40L, 2L, "amount")).isEqualTo(42L);
    assertThat(SafeRedisNumber.multiply(21L, 2L, "amount")).isEqualTo(42L);

    assertThatThrownBy(() -> SafeRedisNumber.add(SafeRedisNumber.MAX_VALUE, 1L, "amount"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> SafeRedisNumber.multiply(SafeRedisNumber.MAX_VALUE, 2L, "amount"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requiresPositiveInputsWhenRequested() {
    assertThatThrownBy(() -> SafeRedisNumber.requirePositive(0L, "stake"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }
}
