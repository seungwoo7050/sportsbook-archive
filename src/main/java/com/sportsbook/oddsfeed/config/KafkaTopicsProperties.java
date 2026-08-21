package com.sportsbook.oddsfeed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oddsfeed.kafka.topics")
public record KafkaTopicsProperties(
    String oddsChanged, String marketStatusChanged, String eventLifecycle, String matchResult) {}
