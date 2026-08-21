package com.sportsbook.risk.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.policy.RiskLimitProperties;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LimitResolverTest {
  private static final UserId USER =
      UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  @Test
  void prefersAUserOverrideAndFallsBackToPolicy() {
    LimitOverrideStore store = mock(LimitOverrideStore.class);
    LimitOverrideField krwDaily = LimitOverrideField.monetary(LimitType.STAKE_DAILY, Currency.KRW);
    when(store.find(USER, krwDaily)).thenReturn(OptionalLong.of(1234));
    LimitResolver resolver = new LimitResolver(defaults(), store);

    assertThat(resolver.resolve(USER, LimitType.STAKE_DAILY, Currency.KRW)).isEqualTo(1234);
    assertThat(resolver.resolve(USER, LimitType.STAKE_WEEKLY, Currency.USD)).isEqualTo(500_000);
  }

  @Test
  void usesOneCurrencyNeutralSelectionOverride() {
    LimitOverrideStore store = mock(LimitOverrideStore.class);
    when(store.find(USER, LimitOverrideField.selections())).thenReturn(OptionalLong.of(12));
    LimitResolver resolver = new LimitResolver(defaults(), store);

    assertThat(resolver.resolve(USER, LimitType.SELECTIONS_PER_MINUTE, Currency.KRW)).isEqualTo(12);
    assertThat(resolver.resolve(USER, LimitType.SELECTIONS_PER_MINUTE, Currency.USD)).isEqualTo(12);
    verify(store, org.mockito.Mockito.times(2)).find(USER, LimitOverrideField.selections());
  }

  private static RiskLimitProperties defaults() {
    return new RiskLimitProperties(null, null, null, null, 0);
  }
}
