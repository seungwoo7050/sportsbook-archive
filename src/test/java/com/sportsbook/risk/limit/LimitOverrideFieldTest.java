package com.sportsbook.risk.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import org.junit.jupiter.api.Test;

class LimitOverrideFieldTest {
  @Test
  void qualifiesOnlyMonetaryFieldsByCurrency() {
    assertThat(LimitOverrideField.monetary(LimitType.STAKE_DAILY, Currency.KRW).redisField())
        .isEqualTo("STAKE_DAILY:KRW");
    assertThat(LimitOverrideField.monetary(LimitType.STAKE_DAILY, Currency.USD).redisField())
        .isEqualTo("STAKE_DAILY:USD");
    assertThat(LimitOverrideField.selections().redisField()).isEqualTo("SELECTIONS_PER_MINUTE");
  }

  @Test
  void rejectsMismatchedDimensions() {
    assertThatThrownBy(
            () -> LimitOverrideField.monetary(LimitType.SELECTIONS_PER_MINUTE, Currency.KRW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LimitOverrideField(LimitType.STAKE_MONTHLY, null))
        .isInstanceOf(NullPointerException.class);
  }
}
