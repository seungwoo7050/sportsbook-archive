package com.sportsbook.settlement.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

  @Bean
  Clock settlementClock() {
    return Clock.systemUTC();
  }
}
