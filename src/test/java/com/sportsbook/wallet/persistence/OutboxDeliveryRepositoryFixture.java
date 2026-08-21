package com.sportsbook.wallet.persistence;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.outbox.OutboxAppender;
import com.sportsbook.wallet.outbox.PendingOutboxMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

abstract class OutboxDeliveryRepositoryFixture {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired OutboxEventRepository events;
  @Autowired WalletOperationRepository operations;
  @Autowired OutboxAppender appender;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeEach
  void clean() {
    events.deleteAll();
    operations.deleteAll();
    jdbc.update("DELETE FROM outbox_stream");
  }

  void persist(String operation, String key, String dedup, Instant created) {
    persist(operation, "wallet.debited.v1", key, dedup, created);
  }

  void persist(String operation, String topic, String key, String dedup, Instant created) {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            ignored -> {
              appender.append(
                  PendingOutboxMessage.create(
                      operation, topic, key, "WalletDebited", dedup, new byte[] {1}, created));
              if (operations.findById(operation).isEmpty()) {
                operations.save(
                    WalletOperation.succeeded(
                        IdempotencyKey.of(operation),
                        WalletCaller.BETTING,
                        WalletOperationKind.BET_DEBIT,
                        UUID.randomUUID(),
                        Money.krw(100L),
                        "0".repeat(64),
                        UUID.randomUUID(),
                        created));
              }
            });
  }
}
