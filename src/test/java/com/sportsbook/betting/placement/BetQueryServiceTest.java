package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.error.BetNotFoundException;
import com.sportsbook.betting.persistence.BetRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class BetQueryServiceTest {

  @Test
  void hidesBetsOwnedByAnotherActor() {
    BetRepository bets = mock(BetRepository.class);
    Bet bet = mock(Bet.class);
    UUID betId = UUID.randomUUID();
    when(bet.userId()).thenReturn(UUID.randomUUID());
    when(bets.findWithLegsByBetId(betId)).thenReturn(Optional.of(bet));

    assertThatThrownBy(() -> new BetQueryService(bets).byId(UUID.randomUUID(), betId))
        .isInstanceOf(BetNotFoundException.class);
  }

  @Test
  void usesBoundedKeysetPaginationAndReturnsTheLastVisibleCursor() {
    BetRepository bets = mock(BetRepository.class);
    UUID actorId = UUID.randomUUID();
    Bet first = mock(Bet.class);
    Bet second = mock(Bet.class);
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    when(first.betId()).thenReturn(firstId);
    when(second.betId()).thenReturn(secondId);
    when(bets.findByUserIdOrderByBetIdDesc(actorId, PageRequest.of(0, 2)))
        .thenReturn(List.of(first, second));

    var page = new BetQueryService(bets).page(actorId, null, 1);

    assertThat(page.items()).containsExactly(first);
    assertThat(page.nextCursor()).isEqualTo(firstId.toString());
    assertThat(page.hasMore()).isTrue();
  }
}
