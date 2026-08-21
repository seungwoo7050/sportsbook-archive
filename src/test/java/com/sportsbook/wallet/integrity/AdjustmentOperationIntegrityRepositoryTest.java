package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import com.sportsbook.wallet.service.WalletAdjustmentService;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import com.sportsbook.wallet.service.command.OpenAccountCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "wallet.outbox.scheduling-enabled=false",
      "wallet.recovery.scheduling-enabled=false"
    })
@Testcontainers
class AdjustmentOperationIntegrityRepositoryTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired WalletService wallet;
  @Autowired WalletAdjustmentService adjustments;
  @Autowired AdjustmentOperationIntegrityRepository integrity;
  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void reconcilesAppliedBlockedAndRejectedOutcomes() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-0000000001c6");
    UUID missingUser = UUID.fromString("019b76da-a000-7000-8000-0000000001c7");
    wallet.openAccount(new OpenAccountCommand(userId, Money.krw(0L).currency()));
    AdjustmentCommand applied = command("1c8", "1cb", userId, 0L, 10L);
    AdjustmentCommand blocked = command("1c9", "1cc", userId, 30L, 0L);
    AdjustmentCommand rejected = command("1ca", "1cd", missingUser, 10L, 0L);

    adjustments.adjust(applied);
    adjustments.adjust(blocked);
    assertThatThrownBy(() -> adjustments.adjust(rejected))
        .isInstanceOf(WalletRejectedException.class);
    assertThat(integrity.findOutcomeDriftKeys()).isEmpty();

    jdbc.update(
        "UPDATE wallet_operation SET request_amount = 11 WHERE idempotency_key = ?",
        applied.idempotencyKey().value());
    assertThat(integrity.findOutcomeDriftKeys()).containsExactly(applied.idempotencyKey().value());
    jdbc.update(
        "UPDATE wallet_operation SET request_amount = 10 WHERE idempotency_key = ?",
        applied.idempotencyKey().value());
  }

  @Test
  void detectsFailureMetadataOnNonRejectedAdjustments() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-0000000001d4");
    wallet.openAccount(new OpenAccountCommand(userId, Money.krw(0L).currency()));
    AdjustmentCommand applied = command("1d5", "1d7", userId, 0L, 10L);
    AdjustmentCommand blocked = command("1d6", "1d8", userId, 30L, 0L);
    adjustments.adjust(applied);
    adjustments.adjust(blocked);

    jdbc.update(
        "UPDATE wallet_operation SET failure_expected_currency = 'KRW' WHERE idempotency_key = ?",
        applied.idempotencyKey().value());
    assertThat(integrity.findOutcomeDriftKeys()).containsExactly(applied.idempotencyKey().value());
    jdbc.update(
        "UPDATE wallet_operation SET failure_expected_currency = NULL WHERE idempotency_key = ?",
        applied.idempotencyKey().value());

    jdbc.update(
        """
        UPDATE wallet_operation SET failure_balance_amount = 1, failure_balance_currency = 'KRW'
        WHERE idempotency_key = ?
        """,
        blocked.idempotencyKey().value());
    assertThat(integrity.findOutcomeDriftKeys()).containsExactly(blocked.idempotencyKey().value());
    jdbc.update(
        """
        UPDATE wallet_operation SET failure_balance_amount = NULL, failure_balance_currency = NULL
        WHERE idempotency_key = ?
        """,
        blocked.idempotencyKey().value());
  }

  private static AdjustmentCommand command(
      String revisionTail, String betTail, UUID userId, long previous, long next) {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000" + revisionTail);
    return new AdjustmentCommand(
        revisionId,
        UUID.fromString("019b76da-a000-7000-8000-000000000" + betTail),
        1L,
        userId,
        Money.krw(previous),
        Money.krw(next),
        IdempotencyKey.of("settlement:revision:" + revisionId));
  }
}
