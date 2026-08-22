package com.sportsbook.betting.placement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.protocol.domain.BetStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class BetReconciliationJobTest {

  @Test
  void resumesOnlyStalePendingBetsInBoundedBatches() {
    BetRepository bets = mock(BetRepository.class);
    BetPlacementService placement = mock(BetPlacementService.class);
    Bet bet = mock(Bet.class);
    UUID betId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    when(bet.betId()).thenReturn(betId);
    when(bets.findByStatusAndCreatedAtBefore(
            BetStatus.PENDING,
            now.minusSeconds(30),
            PageRequest.of(0, BetReconciliationJob.BATCH_SIZE)))
        .thenReturn(List.of(bet));

    new BetReconciliationJob(
            bets, placement, Clock.fixed(now, ZoneOffset.UTC), Duration.ofSeconds(30))
        .reconcile();

    verify(placement).reconcile(betId);
  }
}
