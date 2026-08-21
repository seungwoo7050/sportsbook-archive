package com.sportsbook.oddsfeed.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({MockProperties.class, RealProperties.class})
public class ApplicationConfig {

  @Bean
  public Clock systemClock() {
    return Clock.systemUTC();
  }
}
