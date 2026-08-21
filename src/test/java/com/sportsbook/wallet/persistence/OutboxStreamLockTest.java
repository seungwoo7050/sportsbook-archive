package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
@Import(OutboxStreamLock.class)
class OutboxStreamLockTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired OutboxStreamLock streams;
  @Autowired PlatformTransactionManager transactions;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void serializesPositionsUntilTheEarlierTransactionCommits() throws Exception {
    TransactionTemplate transaction = new TransactionTemplate(transactions);
    CountDownLatch allocated = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch contenderStarted = new CountDownLatch(1);
    CompletableFuture<Long> first =
        CompletableFuture.supplyAsync(
            () ->
                transaction.execute(
                    ignored -> {
                      long sequence = streams.nextSequence("wallet.debited.v1", "user-serial");
                      allocated.countDown();
                      await(release);
                      return sequence;
                    }));
    allocated.await();
    CompletableFuture<Long> second =
        CompletableFuture.supplyAsync(
            () ->
                transaction.execute(
                    ignored -> {
                      contenderStarted.countDown();
                      return streams.nextSequence("wallet.debited.v1", "user-serial");
                    }));
    contenderStarted.await();

    assertThat(second).isNotDone();
    release.countDown();
    assertThat(first.get()).isEqualTo(1L);
    assertThat(second.get()).isEqualTo(2L);
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
