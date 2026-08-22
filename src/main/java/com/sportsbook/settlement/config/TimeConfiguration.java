package com.sportsbook.settlement.config;

import com.sportsbook.settlement.readmodel.BetPlacementFingerprinter;
import com.sportsbook.settlement.readmodel.BetPlacementValidator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

  @Bean
  Clock settlementClock() {
    return Clock.systemUTC();
  }

  @Bean
  BetPlacementValidator betPlacementValidator() {
    return new BetPlacementValidator();
  }

  @Bean
  BetPlacementFingerprinter betPlacementFingerprinter() {
    return new BetPlacementFingerprinter();
  }
}
