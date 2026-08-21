package com.sportsbook.risk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideField;
import org.junit.jupiter.api.Test;

class LimitOverrideTargetTest {
  @Test
  void preservesMonetaryCurrencyAndNeutralizesSelectionCounts() {
    assertThat(new LimitOverrideTarget(LimitType.STAKE_MONTHLY, Currency.USD).field())
        .isEqualTo(LimitOverrideField.monetary(LimitType.STAKE_MONTHLY, Currency.USD));
    assertThat(new LimitOverrideTarget(LimitType.SELECTIONS_PER_MINUTE, null).field())
        .isEqualTo(LimitOverrideField.selections());
  }

  @Test
  void rejectsMismatchedCurrencyScopes() {
    assertThatThrownBy(() -> new LimitOverrideTarget(LimitType.STAKE_DAILY, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LimitOverrideTarget(LimitType.SELECTIONS_PER_MINUTE, Currency.KRW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
