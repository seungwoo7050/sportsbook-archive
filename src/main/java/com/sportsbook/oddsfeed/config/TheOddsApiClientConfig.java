package com.sportsbook.oddsfeed.config;

import com.sportsbook.oddsfeed.provider.real.RateLimiter;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@Profile("real")
public class TheOddsApiClientConfig {

  @Bean
  public WebClient theOddsWebClient(RealProperties properties) {
    return WebClient.builder().baseUrl(properties.baseUrl()).build();
  }

  @Bean
  public RateLimiter theOddsRateLimiter(RealProperties properties, Clock clock) {
    return new RateLimiter(properties.rateLimit().maxRequestsPerMinute(), clock);
  }
}
