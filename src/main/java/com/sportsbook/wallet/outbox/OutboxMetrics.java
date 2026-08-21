package com.sportsbook.wallet.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Delivery counters and a scrape-safe in-memory backlog view. */
@Component
public class OutboxMetrics {

  private final Counter claimed;
  private final Counter published;
  private final Counter retried;
  private final Counter fencedCompletion;
  private final Counter leaseTakeovers;

  public OutboxMetrics(MeterRegistry registry) {
    claimed = registry.counter("wallet.outbox.claimed");
    published = registry.counter("wallet.outbox.published");
    retried = registry.counter("wallet.outbox.retried");
    fencedCompletion = registry.counter("wallet.outbox.fenced.completion");
    leaseTakeovers = registry.counter("wallet.outbox.lease.takeovers");
  }

  public void claimed(List<LeasedOutboxMessage> messages) {
    Objects.requireNonNull(messages, "messages");
    claimed.increment(messages.size());
    leaseTakeovers.increment(messages.stream().filter(LeasedOutboxMessage::leaseTakeover).count());
  }

  public void published(boolean fenceWon) {
    recordCompletion(published, fenceWon);
  }

  public void retried(boolean fenceWon) {
    recordCompletion(retried, fenceWon);
  }

  private void recordCompletion(Counter accepted, boolean fenceWon) {
    if (fenceWon) {
      accepted.increment();
    } else {
      fencedCompletion.increment();
    }
  }
}
