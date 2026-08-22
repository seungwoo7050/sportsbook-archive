package com.sportsbook.betting.placement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetDraft;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.betting.persistence.PlacementRequestRepository;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class BetStoreTest {

  @Test
  void claimsBetBeforePublishingItsRequestPointer() {
    BetRepository bets = mock(BetRepository.class);
    PlacementRequestRepository requests = mock(PlacementRequestRepository.class);
    Bet bet = pendingBet();

    new BetStore(bets, requests).savePending(bet);

    InOrder order = inOrder(bets, requests);
    order.verify(bets).saveAndFlush(bet);
    order.verify(requests).saveAndFlush(any(PlacementRequest.class));
  }

  private static Bet pendingBet() {
    UUID betId = UUID.randomUUID();
    BetDraft draft =
        new BetDraft(
            betId,
            UUID.randomUUID(),
            "B-2026-08-22-00000000",
            new BetSlipType.Single(),
            Money.krw(1_000),
            Money.krw(2_000),
            IdempotencyKey.of("request-1"),
            "a".repeat(64),
            Instant.EPOCH);
    BetLeg leg =
        BetLeg.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2"));
    return Bet.pending(draft, List.of(leg));
  }
}
