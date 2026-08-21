package com.sportsbook.oddsfeed.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oddsfeed.delivery")
public record CriticalDeliveryProperties(
    String streamKey,
    String consumerGroup,
    String consumerName,
    int batchSize,
    Duration claimIdle) {}
