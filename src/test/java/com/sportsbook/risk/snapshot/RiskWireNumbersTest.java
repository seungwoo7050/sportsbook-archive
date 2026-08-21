package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.risk.policy.SafeRedisNumber;
import org.junit.jupiter.api.Test;

class RiskWireNumbersTest {
  @Test
  void parsesCanonicalExactIntegers() {
    assertThat(RiskWireNumbers.exact("0", "value")).isZero();
    assertThat(RiskWireNumbers.exact(Long.toString(SafeRedisNumber.MAX_VALUE), "value"))
        .isEqualTo(SafeRedisNumber.MAX_VALUE);
  }

  @Test
  void rejectsNoncanonicalOrInexactValues() {
    for (String value : new String[] {"", "01", "-1", "+1", "1.0", " 1"}) {
      assertThatThrownBy(() -> RiskWireNumbers.exact(value, "value"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("value");
    }
    assertThatThrownBy(
            () -> RiskWireNumbers.exact(Long.toString(SafeRedisNumber.MAX_VALUE + 1), "value"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("value");
  }

  @Test
  void rejectsMissingValues() {
    assertThatThrownBy(() -> RiskWireNumbers.exact(null, "value"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("value");
  }
}
