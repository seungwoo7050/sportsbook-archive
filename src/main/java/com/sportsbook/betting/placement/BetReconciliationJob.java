package com.sportsbook.betting.placement;

import com.sportsbook.betting.persistence.BetRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BetReconciliationJob {

  private static final Logger log = LoggerFactory.getLogger(BetReconciliationJob.class);
  static final int BATCH_SIZE = 100;

  private final BetRepository bets;
  private final BetPlacementService placement;
  private final String owner;
  private final Duration pendingTimeout;
  private final Duration leaseDuration;
  private final Duration retryDelay;

  public BetReconciliationJob(
      BetRepository bets,
      BetPlacementService placement,
      @Value("${random.uuid}") String owner,
      @Value("${betting.reconciliation.pending-timeout:30s}") Duration pendingTimeout,
      @Value("${betting.reconciliation.lease-duration:30s}") Duration leaseDuration,
      @Value("${betting.reconciliation.retry-delay:10s}") Duration retryDelay) {
    this.bets = bets;
    this.placement = placement;
    if (owner == null || owner.isBlank() || owner.length() > 128) {
      throw new IllegalArgumentException("Reconciliation claim owner must be 1-128 characters");
    }
    this.owner = owner;
    this.pendingTimeout = positive(pendingTimeout, "pendingTimeout");
    this.leaseDuration = positive(leaseDuration, "leaseDuration");
    this.retryDelay = positive(retryDelay, "retryDelay");
  }

  @Scheduled(
      fixedDelayString = "${betting.reconciliation.poll-interval-ms:10000}",
      scheduler = "reconciliationTaskScheduler")
  public void reconcile() {
    List<UUID> claimed =
        bets.claimReconciliationBatch(
            owner,
            pendingTimeout.toMillis(),
            leaseDuration.toMillis(),
            retryDelay.toMillis(),
            BATCH_SIZE);
    for (UUID betId : claimed) {
      try {
        placement.reconcile(betId);
      } catch (RuntimeException unexpected) {
        log.error("Placement reconciliation failed for bet {}", betId, unexpected);
      } finally {
        if (bets.clearReconciliationClaim(betId, owner) == 0) {
          log.warn("Reconciliation claim was no longer owned for bet {}", betId);
        }
      }
    }
  }

  private static Duration positive(Duration value, String name) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }
}
