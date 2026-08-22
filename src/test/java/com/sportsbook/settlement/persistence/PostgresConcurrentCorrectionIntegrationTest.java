package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.correction.CorrectionFanout;
import com.sportsbook.settlement.correction.RevisionWalletGateway;
import com.sportsbook.settlement.result.AcceptedResultRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class PostgresConcurrentCorrectionIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private AcceptedResultRepository acceptedResults;
  @Autowired private CorrectionFanout corrections;
  @MockBean private RevisionWalletGateway wallet;

  @Test
  void concurrentFanoutOwnsOnePlanAndOneWalletAdjustment() throws Exception {
    PendingBet bet = insertPendingBet(UUID.randomUUID());
    Instant sourceTime = Instant.parse("2026-08-22T00:00:00Z");
    UUID previous =
        insertResultCandidate(
            bet.eventId(), bet.selectionId(), SettlementResult.LOST, sourceTime, "SUPERSEDED");
    UUID accepted =
        insertResultCandidate(
            bet.eventId(),
            bet.selectionId(),
            SettlementResult.WON,
            sourceTime.plusSeconds(1),
            "ACCEPTED");
    settleBet(bet, previous, SettlementResult.LOST, 0);
    acceptResult(bet, accepted, SettlementResult.WON, sourceTime.plusSeconds(1));
    var snapshot = acceptedResults.findByEventId(bet.eventId()).orElseThrow();
    CountDownLatch walletEntered = new CountDownLatch(1);
    CountDownLatch releaseWallet = new CountDownLatch(1);
    when(wallet.submit(any()))
        .thenAnswer(
            call -> {
              walletEntered.countDown();
              assertThat(releaseWallet.await(5, TimeUnit.SECONDS)).isTrue();
              return CorrectionProofs.applied(call.getArgument(0));
            });
    var workers = Executors.newFixedThreadPool(2);
    try {
      var first = workers.submit(() -> corrections.fanOut(snapshot));
      assertThat(walletEntered.await(5, TimeUnit.SECONDS)).isTrue();
      var second = workers.submit(() -> corrections.fanOut(snapshot));

      assertThat(second.get(5, TimeUnit.SECONDS)).isEmpty();
      releaseWallet.countDown();
      assertThat(first.get(5, TimeUnit.SECONDS)).hasSize(1);
    } finally {
      releaseWallet.countDown();
      workers.shutdownNow();
    }

    verify(wallet, times(1)).submit(any());
    assertThat(jdbc.queryForObject("select count(*) from settlement_revision", Integer.class))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_event where schema_name='BetResolutionRevised'",
                Integer.class))
        .isOne();
  }
}
