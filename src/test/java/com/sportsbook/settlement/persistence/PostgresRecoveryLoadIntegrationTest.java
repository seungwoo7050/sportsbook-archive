package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.execution.SettlementAttempt;
import com.sportsbook.settlement.execution.SettlementAttemptDraft;
import com.sportsbook.settlement.execution.SettlementAttemptRepository;
import com.sportsbook.settlement.execution.SettlementFinalizer;
import com.sportsbook.settlement.execution.SettlementMoneyPlan;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresRecoveryLoadIntegrationTest extends PostgresIntegrationSupport {

  private static final int ATTEMPTS = 128;

  @Autowired private SettlementAttemptRepository attempts;
  @Autowired private SettlementFinalizer finalizer;

  @Test
  void distributesClaimsAndFencesTheSupersededOwner() throws Exception {
    List<SettlementAttempt> original = new ArrayList<>(ATTEMPTS);
    for (int index = 0; index < ATTEMPTS; index++) {
      PendingBet bet = insertPendingBet(UUID.randomUUID());
      original.add(attempts.claimPending(draft(bet), Duration.ofSeconds(30)).orElseThrow());
    }
    jdbc.update("update bet_selection set outcome = 'WON'");
    jdbc.update(
        "update settlement_attempt set lease_until = current_timestamp - interval '1 second'");

    var recovered = RecoveryClaimLoadHarness.claim(attempts, 4, 32);

    assertThat(recovered).hasSize(ATTEMPTS);
    assertThat(recovered).extracting(item -> item.attempt().betId()).doesNotHaveDuplicates();
    assertThat(recovered)
        .extracting(item -> item.attempt().lease().token())
        .doesNotHaveDuplicates();
    assertThat(recovered)
        .allSatisfy(item -> assertThat(item.attempt().attemptCount()).isEqualTo(2));
    SettlementAttempt stale = original.get(0);
    SettlementAttempt owner =
        recovered.stream()
            .map(item -> item.attempt())
            .filter(attempt -> attempt.betId().equals(stale.betId()))
            .findFirst()
            .orElseThrow();

    assertThat(finalizer.settle(stale)).isFalse();
    assertThat(finalizer.settle(owner)).isTrue();
    assertThat(finalizer.settle(owner)).isFalse();
    assertThat(jdbc.queryForObject("select count(*) from bet where status='SETTLED'", Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from outbox_event", Long.class)).isEqualTo(1);
  }

  private static SettlementAttemptDraft draft(PendingBet bet) {
    SettlementMoneyPlan money =
        new SettlementMoneyPlan(
            Money.krw(100), Money.krw(200), Money.krw(100), Money.krw(0), Money.krw(100));
    return SettlementAttemptDraft.resolved(bet.betId(), bet.eventId(), SettlementResult.WON, money);
  }
}
