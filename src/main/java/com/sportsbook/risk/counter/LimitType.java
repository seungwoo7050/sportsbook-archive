package com.sportsbook.risk.counter;

import java.time.Duration;

/** Sliding policy dimensions and their Redis retention windows. */
public enum LimitType {
  STAKE_DAILY("stake-daily", Duration.ofDays(1), Measure.SUM),
  STAKE_WEEKLY("stake-weekly", Duration.ofDays(7), Measure.SUM),
  STAKE_MONTHLY("stake-monthly", Duration.ofDays(30), Measure.SUM),
  SELECTIONS_PER_MINUTE("selections-per-minute", Duration.ofMinutes(1), Measure.COUNT);

  private final String suffix;
  private final Duration window;
  private final Measure measure;

  LimitType(String suffix, Duration window, Measure measure) {
    this.suffix = suffix;
    this.window = window;
    this.measure = measure;
  }

  public String suffix() {
    return suffix;
  }

  public Duration window() {
    return window;
  }

  public Measure measure() {
    return measure;
  }

  public boolean currencyScoped() {
    return measure == Measure.SUM;
  }

  public enum Measure {
    SUM,
    COUNT
  }
}
