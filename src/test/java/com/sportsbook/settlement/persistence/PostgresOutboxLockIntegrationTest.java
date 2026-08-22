package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.outbox.OutboxEvent;
import com.sportsbook.settlement.outbox.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresOutboxLockIntegrationTest extends PostgresIntegrationSupport {

  @Autowired private OutboxEventRepository outbox;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void skipsRowsHeldByAnotherPublisherTransaction() throws Exception {
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    OutboxEvent first = pending("first");
    OutboxEvent second = pending("second");
    transactions.executeWithoutResult(ignored -> outbox.saveAllAndFlush(List.of(first, second)));
    CountDownLatch firstLocked = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    var workers = Executors.newSingleThreadExecutor();

    try {
      var held =
          workers.submit(
              () ->
                  transactions.execute(
                      ignored -> {
                        UUID id = outbox.lockNextUnpublished(1).get(0).eventId();
                        firstLocked.countDown();
                        await(releaseFirst);
                        return id;
                      }));
      assertThat(firstLocked.await(2, TimeUnit.SECONDS)).isTrue();

      UUID skipped =
          transactions.execute(ignored -> outbox.lockNextUnpublished(1).get(0).eventId());
      releaseFirst.countDown();

      assertThat(List.of(held.get(2, TimeUnit.SECONDS), skipped))
          .containsExactlyInAnyOrder(first.eventId(), second.eventId());
    } finally {
      releaseFirst.countDown();
      workers.shutdownNow();
    }
  }

  private static OutboxEvent pending(String key) {
    return OutboxEvent.pending("bet.settled.v1", key, "BetSettled", new byte[] {1}, Instant.EPOCH);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(2, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for publisher release");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }
}
