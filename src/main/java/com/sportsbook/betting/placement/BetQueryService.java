package com.sportsbook.betting.placement;

import com.sportsbook.betting.api.CursorPage;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.error.BetNotFoundException;
import com.sportsbook.betting.persistence.BetRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BetQueryService {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  private final BetRepository bets;

  public BetQueryService(BetRepository bets) {
    this.bets = bets;
  }

  @Transactional(readOnly = true)
  public Bet byId(UUID actorId, UUID betId) {
    return bets.findWithLegsByBetId(betId)
        .filter(bet -> bet.userId().equals(actorId))
        .orElseThrow(() -> new BetNotFoundException("No bet with id " + betId));
  }

  @Transactional(readOnly = true)
  public CursorPage<Bet> page(UUID actorId, UUID cursor, Integer requestedLimit) {
    int limit =
        requestedLimit == null || requestedLimit <= 0
            ? DEFAULT_LIMIT
            : Math.min(requestedLimit, MAX_LIMIT);
    PageRequest probe = PageRequest.of(0, limit + 1);
    List<Bet> rows =
        cursor == null
            ? bets.findByUserIdOrderByBetIdDesc(actorId, probe)
            : bets.findByUserIdAndBetIdLessThanOrderByBetIdDesc(actorId, cursor, probe);
    boolean hasMore = rows.size() > limit;
    List<Bet> items = hasMore ? rows.subList(0, limit) : rows;
    String next =
        hasMore && !items.isEmpty() ? items.get(items.size() - 1).betId().toString() : null;
    return new CursorPage<>(List.copyOf(items), next, hasMore);
  }
}
