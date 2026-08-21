package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.outbox.OutboxEvent;
import com.sportsbook.wallet.outbox.PendingOutboxMessage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
class OutboxEventRepositoryTest {
  private static final Instant NOW = Instant.parse("2999-01-06T00:00:00Z");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000030");

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;
  @Autowired OutboxEventRepository events;
  @Autowired WalletOperationRepository operations;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void persistsOrderedRowsAndRejectsSemanticDuplicates() {
    operations.saveAllAndFlush(java.util.List.of(operation("outbox:one"), operation("outbox:two")));
    jdbc.update(
        "INSERT INTO outbox_stream(topic, partition_key) VALUES (?, ?)",
        "wallet.debited.v1",
        USER_ID.toString());
    PendingOutboxMessage first = message("outbox:one", "bet-1");
    events.saveAndFlush(OutboxEvent.pending(first, 1L));

    assertThat(events.findAllByOrderByTopicAscPartitionKeyAscStreamSequenceAsc())
        .singleElement()
        .satisfies(
            stored -> {
              assertThat(stored.eventId()).isEqualTo(first.eventId());
              assertThat(stored.streamSequence()).isEqualTo(1L);
            });
    assertThat(
            jdbc.queryForObject(
                "SELECT available_at < created_at FROM outbox_event WHERE event_id=?",
                Boolean.class,
                first.eventId()))
        .isTrue();
    assertThatThrownBy(
            () -> events.saveAndFlush(OutboxEvent.pending(message("outbox:two", "bet-1"), 2L)))
        .isInstanceOf(DataIntegrityViolationException.class);
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
