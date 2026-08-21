package com.sportsbook.wallet.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Delivery counters and a scrape-safe in-memory backlog view. */
@Component
public class OutboxMetrics {

  private final Counter claimed;
  private final Counter published;
  private final Counter retried;
  private final Counter fencedCompletion;
  private final Counter leaseTakeovers;
  private final AtomicReference<OutboxBacklogSnapshot> backlog =
      new AtomicReference<>(OutboxBacklogSnapshot.EMPTY);

  public OutboxMetrics(MeterRegistry registry) {
    claimed = registry.counter("wallet.outbox.claimed");
    published = registry.counter("wallet.outbox.published");
    retried = registry.counter("wallet.outbox.retried");
    fencedCompletion = registry.counter("wallet.outbox.fenced.completion");
    leaseTakeovers = registry.counter("wallet.outbox.lease.takeovers");
    Gauge.builder("wallet.outbox.pending", backlog, value -> value.get().pending())
        .register(registry);
    Gauge.builder("wallet.outbox.leased", backlog, value -> value.get().leased())
        .register(registry);
    Gauge.builder(
            "wallet.outbox.oldest.pending.seconds",
            backlog,
            value -> value.get().oldestPendingSeconds())
        .register(registry);
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

  public void sample(OutboxBacklogSnapshot snapshot) {
    backlog.set(Objects.requireNonNull(snapshot, "snapshot"));
  }

  private void recordCompletion(Counter accepted, boolean fenceWon) {
    if (fenceWon) {
      accepted.increment();
    } else {
      fencedCompletion.increment();
    }
  }
}
