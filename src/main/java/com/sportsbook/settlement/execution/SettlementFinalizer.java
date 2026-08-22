package com.sportsbook.settlement.execution;

import com.sportsbook.settlement.config.SettlementTopics;
import com.sportsbook.settlement.domain.Bet;
import com.sportsbook.settlement.domain.SettlementStatus;
import com.sportsbook.settlement.outbox.OutboxEventRepository;
import com.sportsbook.settlement.outbox.SettlementEventFactory;
import com.sportsbook.settlement.outbox.StrictAvroEncoder;
import com.sportsbook.settlement.persistence.BetRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SettlementFinalizer {

  private final BetRepository bets;
  private final SettlementAttemptRepository attempts;
  private final OutboxEventRepository outbox;
  private final SettlementEventFactory events;

  public SettlementFinalizer(
      BetRepository bets,
      SettlementAttemptRepository attempts,
      OutboxEventRepository outbox,
      SettlementTopics topics) {
    this.bets = bets;
    this.attempts = attempts;
    this.outbox = outbox;
    this.events = new SettlementEventFactory(topics, new StrictAvroEncoder());
  }

  @Transactional
  public boolean settle(SettlementAttempt attempt, Instant now) {
    if (attempt.action() != SettlementAttempt.Action.SETTLE) {
      throw new IllegalArgumentException("Resolved finalization requires SETTLE action");
    }
    Bet bet = bets.findForUpdateById(attempt.betId()).orElseThrow();
    if (bet.status() != SettlementStatus.PENDING || !attempts.consumeLease(attempt)) {
      return false;
    }
    bet.recordSettled(attempt.result(), attempt.money().payout(), now);
    outbox.save(events.settled(bet, attempt.eventId()));
    return true;
  }
}
