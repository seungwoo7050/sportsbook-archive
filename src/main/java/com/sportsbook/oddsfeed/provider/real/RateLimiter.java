package com.sportsbook.oddsfeed.provider.real;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

public final class RateLimiter {

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final int maxRequestsPerWindow;
  private final Clock clock;
  private final Deque<Instant> recent = new ArrayDeque<>();

  public RateLimiter(int maxRequestsPerWindow, Clock clock) {
    this.maxRequestsPerWindow = maxRequestsPerWindow;
    this.clock = clock;
  }

  public synchronized boolean tryAcquire() {
    Instant now = clock.instant();
    Instant cutoff = now.minus(WINDOW);
    while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
      recent.pollFirst();
    }
    if (recent.size() >= maxRequestsPerWindow) {
      return false;
    }
    recent.addLast(now);
    return true;
  }

  synchronized int currentUsage() {
    return recent.size();
  }
}
