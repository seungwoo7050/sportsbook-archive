package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.admin.AdminAction;
import com.sportsbook.settlement.admin.AdminActionRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresAdminActionRepositoryIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AdminActionRepository actions;
  @Autowired private TransactionTemplate transactions;

  @BeforeEach
  void clearActions() {
    jdbc.execute("truncate table settlement_admin_action");
  }

  @Test
  void locksAndReturnsTheFinalDatabaseTimedAction() {
    UUID key = UUID.randomUUID();
    UUID target = UUID.randomUUID();
    String fingerprint = "b".repeat(64);

    AdminAction created =
        transactions.execute(
            ignored -> {
              assertThat(actions.lockAndFind(key)).isEmpty();
              return actions.append(
                  key,
                  AdminAction.Kind.CANDIDATE_APPROVE,
                  target,
                  fingerprint,
                  AdminAction.Outcome.CANDIDATE_APPROVED,
                  null);
            });
    AdminAction replay = transactions.execute(ignored -> actions.lockAndFind(key).orElseThrow());

    assertThat(created).isEqualTo(replay);
    assertThat(created.createdAt()).isEqualTo(created.completedAt());
    assertThat(created.targetId()).isEqualTo(target);
    assertThat(jdbc.queryForObject("select count(*) from settlement_admin_action", Integer.class))
        .isOne();
  }
}
