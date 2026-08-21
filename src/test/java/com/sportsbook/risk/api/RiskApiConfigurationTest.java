package com.sportsbook.risk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideStore;
import com.sportsbook.risk.policy.RiskLimitProperties;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskApiConfigurationTest {
  @Test
  void suppliesUtcTimeAndPolicyFallbacks() {
    RiskApiConfiguration configuration = new RiskApiConfiguration();
    RiskLimitProperties defaults = new RiskLimitProperties(null, null, null, null, 0);
    var resolver = configuration.limitResolver(defaults, mock(LimitOverrideStore.class));

    assertThat(configuration.riskClock().getZone()).isEqualTo(ZoneOffset.UTC);
    assertThat(resolver.resolve(UserId.of(new UUID(0, 1)), LimitType.STAKE_DAILY, Currency.KRW))
        .isEqualTo(defaults.limit(LimitType.STAKE_DAILY, Currency.KRW));
  }
}
