package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.persistence.OutboxEventRepository;
import com.sportsbook.wallet.persistence.OutboxStreamLock;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
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
@Import({OutboxAppender.class, OutboxStreamLock.class})
class OutboxAppenderTest {
  private static final Instant NOW = Instant.parse("2026-01-07T00:00:00Z");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000031");

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired OutboxAppender appender;
  @Autowired OutboxEventRepository events;
  @Autowired WalletOperationRepository operations;
  @Autowired PlatformTransactionManager transactions;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void appendsDeferredOperationEventsAtContiguousPositions() {
    assertThatThrownBy(() -> appender.append(message("append:outside", "outside")))
        .isInstanceOf(IllegalTransactionStateException.class);
    TransactionTemplate transaction = new TransactionTemplate(transactions);

    transaction.executeWithoutResult(
        ignored -> {
          appender.append(message("append:one", "bet-1"));
          operations.save(operation("append:one"));
          appender.append(message("append:two", "bet-2"));
          operations.save(operation("append:two"));
        });

    assertThat(events.findAllByOrderByTopicAscPartitionKeyAscStreamSequenceAsc())
        .extracting(OutboxEvent::streamSequence)
        .containsExactly(1L, 2L);
  }

  private static PendingOutboxMessage message(String operationKey, String deduplicationKey) {
    return PendingOutboxMessage.create(
        operationKey,
        "wallet.debited.v1",
        USER_ID.toString(),
        "WalletDebited",
        deduplicationKey,
        new byte[] {1},
        NOW);
  }

  private static WalletOperation operation(String key) {
    return WalletOperation.succeeded(
        IdempotencyKey.of(key),
        WalletCaller.PLATFORM,
        WalletOperationKind.DEPOSIT,
        USER_ID,
        Money.krw(1L),
        "a".repeat(64),
        UUID.randomUUID(),
        NOW);
  }
}
