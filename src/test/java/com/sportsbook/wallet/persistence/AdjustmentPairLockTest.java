package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(AdjustmentPairLock.class)
class AdjustmentPairLockTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired AdjustmentPairLock pairLock;
  @Autowired PlatformTransactionManager transactions;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void serializesTheSameBetRevisionUntilTransactionCompletion() throws Exception {
    UUID betId = UUID.fromString("019b76da-a000-7000-8000-000000000121");
    TransactionTemplate transaction = new TransactionTemplate(transactions);
    CountDownLatch acquired = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch contenderStarted = new CountDownLatch(1);
    CompletableFuture<Void> first =
        CompletableFuture.runAsync(
            () ->
                transaction.executeWithoutResult(
                    ignored -> {
                      pairLock.acquire(betId, 7L);
                      acquired.countDown();
                      await(release);
                    }));
    acquired.await();
    CompletableFuture<Void> second =
        CompletableFuture.runAsync(
            () ->
                transaction.executeWithoutResult(
                    ignored -> {
                      contenderStarted.countDown();
                      pairLock.acquire(betId, 7L);
                    }));
    contenderStarted.await();

    try {
      assertThatThrownBy(() -> second.get(300L, java.util.concurrent.TimeUnit.MILLISECONDS))
          .isInstanceOf(java.util.concurrent.TimeoutException.class);
    } finally {
      release.countDown();
    }
    first.get();
    second.get();
  }

  @Test
  void refusesAnAutocommitLockThatWouldReleaseImmediately() {
    assertThatThrownBy(() -> pairLock.acquire(UUID.randomUUID(), 1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Adjustment pair lock requires an active transaction");
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
