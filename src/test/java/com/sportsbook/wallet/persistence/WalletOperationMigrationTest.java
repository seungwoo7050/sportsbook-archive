package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletOperationMigrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void persistsTerminalObservationsAcrossAClockRegression() {
    jdbc.update(
        """
        INSERT INTO wallet_operation (
            idempotency_key, caller_id, operation_kind, user_id,
            request_amount, request_currency, request_fingerprint, status,
            operation_group_id, requested_at, updated_at, completed_at
        ) VALUES (
            'operation:reverse-time', 'PLATFORM', 'DEPOSIT', ?,
            1, 'KRW', ?, 'SUCCEEDED', ?,
            TIMESTAMPTZ '2026-01-01 00:00:03Z',
            TIMESTAMPTZ '2026-01-01 00:00:02Z',
            TIMESTAMPTZ '2026-01-01 00:00:01Z'
        )
        """,
        UUID.fromString("019b76da-a000-7000-8000-000000000014"),
        "a".repeat(64),
        UUID.fromString("019b76da-a000-7000-8000-000000000015"));

    assertThat(
            jdbc.queryForObject(
                """
                SELECT updated_at < requested_at AND completed_at < requested_at
                FROM wallet_operation WHERE idempotency_key='operation:reverse-time'
                """,
                Boolean.class))
        .isTrue();
  }
}
