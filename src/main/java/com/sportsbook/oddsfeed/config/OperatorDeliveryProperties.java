package com.sportsbook.oddsfeed.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis Stream settings for durable operator actions. */
@ConfigurationProperties(prefix = "oddsfeed.operator.delivery")
public record OperatorDeliveryProperties(
    String streamKey,
    String consumerGroup,
    String consumerName,
    int batchSize,
    Duration claimIdle,
    long pollIntervalMs) {}
