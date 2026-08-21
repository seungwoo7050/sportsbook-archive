package com.sportsbook.oddsfeed.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oddsfeed.cache")
public record CacheProperties(Duration ttl) {}
