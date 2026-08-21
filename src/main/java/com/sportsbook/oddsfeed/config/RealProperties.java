package com.sportsbook.oddsfeed.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oddsfeed.real")
public record RealProperties(
    String apiKey,
    String baseUrl,
    List<String> sportKeys,
    RateLimit rateLimit,
    int monthlyQuota,
    int pollIntervalSeconds) {

  public record RateLimit(int maxRequestsPerMinute) {}
}
