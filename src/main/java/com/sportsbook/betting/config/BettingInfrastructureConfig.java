package com.sportsbook.betting.config;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BettingInfrastructureConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  static boolean isUtc(Clock clock) {
    return ZoneOffset.UTC.equals(clock.getZone());
  }
}
