package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.admin.AdminCandidateApproval;
import com.sportsbook.settlement.admin.AdminCandidateRejection;
import com.sportsbook.settlement.admin.AdminControlException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PostgresAdminCandidateConcurrencyIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AdminCandidateApproval approvals;
  @Autowired private AdminCandidateRejection rejections;

  @BeforeEach
  void clearAdminActions() {
    jdbc.execute("truncate table settlement_admin_action");
  }

  @Test
  void commitsOnlyOneConcurrentDecisionAndAction() throws Exception {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    UUID candidateId =
        insertResultCandidate(
            bet.eventId(), bet.selectionId(), SettlementResult.WON, Instant.EPOCH, "PENDING");
    CountDownLatch start = new CountDownLatch(1);
    var workers = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> approve =
          workers.submit(
              () -> decide(start, () -> approvals.decide(UUID.randomUUID(), candidateId)));
      Future<Boolean> reject =
          workers.submit(
              () ->
                  decide(
                      start,
                      () ->
                          rejections.decide(
                              UUID.randomUUID(), candidateId, "concurrent rejection")));
      start.countDown();

      assertThat(List.of(approve.get(), reject.get())).containsExactlyInAnyOrder(true, false);
    } finally {
      workers.shutdownNow();
    }
    assertThat(jdbc.queryForObject("select count(*) from settlement_admin_action", Integer.class))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "select state in ('ACCEPTED','REJECTED') from result_candidate "
                    + "where candidate_id=?",
                Boolean.class,
                candidateId))
        .isTrue();
  }

  private static boolean decide(CountDownLatch start, Command command) throws Exception {
    start.await();
    try {
      command.run();
      return true;
    } catch (AdminControlException conflict) {
      return false;
    }
  }

  @FunctionalInterface
  private interface Command {
    void run();
  }
}
