package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
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
class WalletPersistenceTest {
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
  void migratesFinalConstraintsAndAdjustmentReason() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000010");
    jdbc.update(
        """
        INSERT INTO account (
            user_id, available_currency, locked_currency, created_at, updated_at
        ) VALUES (?, 'KRW', 'KRW', now(), now())
        """,
        userId);
    jdbc.update(
        """
        INSERT INTO ledger_entry (
            entry_id, account_id, bucket, side, amount, currency, reason,
            idempotency_key, operation_group_id, created_at
        ) VALUES (?, ?, 'AVAILABLE', 'DEBIT', 1, 'KRW', 'BET_ADJUSTMENT', ?, ?, now())
        """,
        UUID.randomUUID(),
        userId,
        "adjustment:persistence",
        UUID.randomUUID());

    BigInteger debt = BigInteger.ONE.shiftLeft(Long.SIZE - 1);
    jdbc.update(
        "UPDATE account SET recovery_debt_amount=?, recovery_frozen_at=now() WHERE user_id=?",
        debt,
        userId);
    jdbc.update(
        "UPDATE account SET updated_at=created_at - interval '1 second' WHERE user_id=?", userId);

    assertThat(
            jdbc.queryForObject(
                    "SELECT recovery_debt_amount FROM account WHERE user_id=?",
                    BigDecimal.class,
                    userId)
                .toBigIntegerExact())
        .isEqualTo(debt);
    assertThat(
            jdbc.queryForObject(
                "SELECT reason FROM ledger_entry WHERE idempotency_key=?",
                String.class,
                "adjustment:persistence"))
        .isEqualTo("BET_ADJUSTMENT");
    assertThat(
            jdbc.queryForObject(
                "SELECT updated_at < created_at FROM account WHERE user_id=?",
                Boolean.class,
                userId))
        .isTrue();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE account SET available_amount=?, locked_amount=? WHERE user_id=?",
                    Long.MAX_VALUE,
                    1L,
                    userId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
