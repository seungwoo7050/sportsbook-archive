package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementMoneyPlan;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresResultActionableQueryIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private BetRepository bets;
  @Autowired private SettlementAttemptRepository attempts;

  @Test
  void excludesPendingBetsThatAlreadyOwnAnImmutableAttempt() {
    UUID eventId = UUID.randomUUID();
    PendingBet actionable = insertPendingBet(eventId);
    PendingBet owned = insertPendingBet(eventId);
    SettlementMoneyPlan money =
        new SettlementMoneyPlan(
            Money.krw(100), Money.krw(200), Money.krw(100), Money.krw(0), Money.krw(100));
    assertThat(
            attempts.claimPending(
                SettlementAttemptDraft.resolved(
                    owned.betId(), eventId, SettlementResult.WON, money),
                Duration.ofSeconds(30)))
        .isPresent();

    assertThat(bets.findResultActionableIdsByEvent(eventId)).containsExactly(actionable.betId());
  }
}
