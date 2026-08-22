package com.sportsbook.betting.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

class BetRepositoryLeaseTest {

  @Test
  void claimsFairBoundedRowsWithDatabaseTimeAndSkipLocked() throws Exception {
    Method claim =
        BetRepository.class.getMethod(
            "claimReconciliationBatch",
            String.class,
            long.class,
            long.class,
            long.class,
            int.class);
    String sql = claim.getAnnotation(Query.class).value();

    assertThat(claim.getAnnotation(Transactional.class)).isNotNull();
    assertThat(sql)
        .contains("CURRENT_TIMESTAMP")
        .contains("ORDER BY COALESCE")
        .contains("FOR UPDATE SKIP LOCKED")
        .contains("LIMIT :batchSize")
        .contains("RETURNING b.bet_id");
  }

  @Test
  void clearsOnlyTheClaimOwnedByThisWorker() throws Exception {
    Method clear =
        BetRepository.class.getMethod("clearReconciliationClaim", UUID.class, String.class);
    String sql = clear.getAnnotation(Query.class).value();

    assertThat(clear.getAnnotation(Modifying.class)).isNotNull();
    assertThat(sql)
        .contains("reconciliation_claim_owner = NULL")
        .contains("reconciliation_claim_owner = :owner");
  }
}
