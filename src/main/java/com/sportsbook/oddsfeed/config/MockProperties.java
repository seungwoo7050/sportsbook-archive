package com.sportsbook.oddsfeed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oddsfeed.mock")
public record MockProperties(
    double minutesPerSecond,
    Scenarios scenarios,
    double baseHomeWinProbability,
    double baseDrawProbability,
    double baseAwayWinProbability,
    long randomSeed,
    int tickIntervalMs) {

  public record Scenarios(boolean autoRotate, int rotationIntervalSeconds) {}
}
