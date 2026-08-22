package com.sportsbook.settlement.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class SettlementMetrics {

  public static final String OPERATIONS = "settlement.operations";
  public static final String DURATION = "settlement.operation.duration";
  private static final Pattern LABEL = Pattern.compile("[a-z0-9_]{1,32}");

  private final MeterRegistry registry;

  public SettlementMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public Timer.Sample start() {
    return Timer.start(registry);
  }

  public void count(String flow, String outcome) {
    count(flow, outcome, 1);
  }

  public void count(String flow, String outcome, long amount) {
    if (amount < 0) {
      throw new IllegalArgumentException("Metric amount must be nonnegative");
    }
    registry.counter(OPERATIONS, "flow", label(flow), "outcome", label(outcome)).increment(amount);
  }

  public void stop(Timer.Sample sample, String flow) {
    Objects.requireNonNull(sample, "sample").stop(registry.timer(DURATION, "flow", label(flow)));
  }

  private static String label(String value) {
    if (value == null || !LABEL.matcher(value).matches()) {
      throw new IllegalArgumentException("Metric labels must be bounded constants");
    }
    return value;
  }
}
