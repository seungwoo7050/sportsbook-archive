package com.sportsbook.wallet.outbox;

/** One database-clock view of unpublished delivery state. */
public record OutboxBacklogSnapshot(long pending, long leased, double oldestPendingSeconds) {

  public static final OutboxBacklogSnapshot EMPTY = new OutboxBacklogSnapshot(0, 0, 0);

  public OutboxBacklogSnapshot {
    if (pending < 0
        || leased < 0
        || leased > pending
        || oldestPendingSeconds < 0
        || !Double.isFinite(oldestPendingSeconds)) {
      throw new IllegalArgumentException("invalid outbox backlog snapshot");
    }
  }
}
