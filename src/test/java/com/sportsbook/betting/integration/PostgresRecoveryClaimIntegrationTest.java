package com.sportsbook.betting.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.betting.support.PostgresIntegrationSupport;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PostgresRecoveryClaimIntegrationTest extends PostgresIntegrationSupport {

  @Autowired BetRepository bets;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seedPendingBets() {
    jdbc.execute("DELETE FROM bet");
    jdbc.execute(
        """
        INSERT INTO bet (
            bet_id, user_id, bet_reference, slip_type, stake_amount, stake_currency,
            max_payout_amount, max_payout_currency, status, idempotency_key, created_at, updated_at
        )
        SELECT
            ('00000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
            ('10000000-0000-4000-8000-' || lpad(n::text, 12, '0'))::uuid,
            'B-RECOVERY-' || lpad(n::text, 6, '0'), 'SINGLE', 1000, 'KRW',
            2000, 'KRW', 'PENDING', 'recovery-' || n,
            CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '1 hour'
        FROM generate_series(1, 120) n
        """);
  }

  @Test
  void claimsEveryEligibleRowOnceAndRecoversAnExpiredLease() throws Exception {
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<List<UUID>> first = claim("worker-a", workers);
      CompletableFuture<List<UUID>> second = claim("worker-b", workers);
      List<UUID> a = first.get(10, TimeUnit.SECONDS);
      List<UUID> b = second.get(10, TimeUnit.SECONDS);
      HashSet<UUID> all = new HashSet<>(a);

      assertThat(List.of(a.size(), b.size())).containsExactlyInAnyOrder(100, 20);
      assertThat(java.util.Collections.disjoint(a, b)).isTrue();
      all.addAll(b);
      assertThat(all).hasSize(120);

      UUID expired = a.get(0);
      jdbc.update(
          "UPDATE bet SET reconciliation_claim_until = CURRENT_TIMESTAMP - INTERVAL '1 second', "
              + "reconciliation_eligible_at = CURRENT_TIMESTAMP - INTERVAL '1 second' "
              + "WHERE bet_id = ?",
          expired);
      assertThat(bets.claimReconciliationBatch("worker-c", 1, 60_000, 10_000, 1))
          .containsExactly(expired);
      assertThat(bets.clearReconciliationClaim(expired, "worker-a")).isZero();

      jdbc.update("UPDATE bet SET status = 'REJECTED' WHERE bet_id = ?", expired);
      assertThat(bets.claimReconciliationBatch("worker-d", 1, 60_000, 10_000, 1)).isEmpty();
      assertThat(bets.clearReconciliationClaim(expired, "worker-c")).isOne();
    } finally {
      workers.shutdownNow();
      assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private CompletableFuture<List<UUID>> claim(String owner, ExecutorService workers) {
    return CompletableFuture.supplyAsync(
        () -> bets.claimReconciliationBatch(owner, 1, 60_000, 10_000, 100), workers);
  }
}
