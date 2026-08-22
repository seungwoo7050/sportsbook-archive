package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.execution.SettlementAttempt;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementExecution;
import com.sportsbook.settlement.execution.SettlementMoneyPlan;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresRecoveryIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private SettlementAttemptRepository attempts;

  @Test
  void reclaimsExpiredAndReleasedLeasesWithoutTakingActiveWork() {
    Instant now = jdbc.queryForObject("select current_timestamp", Timestamp.class).toInstant();
    PendingBet expiredBet = insertPendingBet(UUID.randomUUID());
    PendingBet releasedBet = insertPendingBet(UUID.randomUUID());
    PendingBet activeBet = insertPendingBet(UUID.randomUUID());
    SettlementAttempt expired = attempt(expiredBet);
    SettlementAttempt released = attempt(releasedBet);
    SettlementAttempt active = attempt(activeBet);
    jdbc.update(
        "update settlement_attempt set lease_until=?, updated_at=? where bet_id=?",
        Timestamp.from(now.minusSeconds(30)),
        Timestamp.from(now.minusSeconds(10)),
        expired.betId());

    assertThat(
            attempts.releaseForRecovery(
                released, new IllegalStateException("secret response body"), now.minusSeconds(5)))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "select last_error from settlement_attempt where bet_id = ?",
                String.class,
                released.betId()))
        .isEqualTo("IllegalStateException");

    List<SettlementExecution> claimed = attempts.claimRecoveryBatch(Duration.ofSeconds(30), 10);

    assertThat(claimed)
        .extracting(execution -> execution.attempt().betId())
        .containsExactly(expired.betId(), released.betId());
    assertThat(claimed)
        .allSatisfy(execution -> assertThat(execution.attempt().attemptCount()).isEqualTo(2));
    assertThat(claimed)
        .allSatisfy(
            execution -> {
              Instant until = execution.attempt().lease().until();
              Instant updated = execution.attempt().updatedAt();
              assertThat(until).isEqualTo(updated.plusSeconds(30));
            });
  }

  private SettlementAttempt attempt(PendingBet bet) {
    SettlementMoneyPlan money =
        new SettlementMoneyPlan(
            Money.krw(100), Money.krw(200), Money.krw(100), Money.krw(0), Money.krw(100));
    return attempts
        .claimPending(
            SettlementAttemptDraft.resolved(
                bet.betId(), bet.eventId(), SettlementResult.WON, money),
            Duration.ofSeconds(30))
        .orElseThrow();
  }
}
