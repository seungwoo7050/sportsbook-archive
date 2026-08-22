package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.execution.SettlementAttempt;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementMoneyPlan;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresInitialAttemptClaimIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private SettlementAttemptRepository attempts;

  @Test
  void persistsOneInitialLeaseFromPostgresqlTime() {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    Instant before = databaseNow();

    SettlementAttempt claimed =
        attempts.claimPending(draft(bet), Duration.ofSeconds(30)).orElseThrow();
    Instant after = databaseNow();

    assertThat(claimed.createdAt()).isBetween(before, after);
    assertThat(claimed.updatedAt()).isEqualTo(claimed.createdAt());
    assertThat(claimed.lease().until()).isEqualTo(claimed.createdAt().plusSeconds(30));
    assertThat(claimed.attemptCount()).isEqualTo(1);
    assertThat(attempts.claimPending(draft(bet), Duration.ofSeconds(30))).isEmpty();
  }

  private Instant databaseNow() {
    return jdbc.queryForObject("select current_timestamp", Timestamp.class).toInstant();
  }

  private static SettlementAttemptDraft draft(PendingBet bet) {
    SettlementMoneyPlan money =
        new SettlementMoneyPlan(
            Money.krw(100), Money.krw(200), Money.krw(100), Money.krw(0), Money.krw(100));
    return SettlementAttemptDraft.resolved(bet.betId(), bet.eventId(), SettlementResult.WON, money);
  }
}
