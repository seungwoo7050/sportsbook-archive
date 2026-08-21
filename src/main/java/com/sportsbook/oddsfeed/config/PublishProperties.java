package com.sportsbook.oddsfeed.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oddsfeed.publish")
public record PublishProperties(BigDecimal oddsChangeThreshold, Duration brokerAckTimeout) {}
