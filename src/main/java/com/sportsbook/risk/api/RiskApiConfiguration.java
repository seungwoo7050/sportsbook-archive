package com.sportsbook.risk.api;

import com.sportsbook.risk.limit.LimitOverrideStore;
import com.sportsbook.risk.limit.LimitResolver;
import com.sportsbook.risk.policy.RiskLimitProperties;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies stateless collaborators shared by the internal HTTP operations. */
@Configuration
public class RiskApiConfiguration {
  @Bean
  Clock riskClock() {
    return Clock.systemUTC();
  }

  @Bean
  LimitResolver limitResolver(RiskLimitProperties defaults, LimitOverrideStore overrides) {
    return new LimitResolver(defaults, overrides);
  }
}
