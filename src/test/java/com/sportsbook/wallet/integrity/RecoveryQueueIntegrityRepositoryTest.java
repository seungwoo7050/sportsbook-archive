package com.sportsbook.wallet.integrity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.service.RecoveryWorker;
import com.sportsbook.wallet.service.WalletAdjustmentService;
import com.sportsbook.wallet.service.WalletService;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import com.sportsbook.wallet.service.command.DepositCommand;
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
class RecoveryQueueIntegrityRepositoryTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired WalletService wallet;
  @Autowired WalletAdjustmentService adjustments;
  @Autowired RecoveryWorker recovery;
  @Autowired RecoveryQueueIntegrityRepository integrity;
  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void reconcilesRecoveredHistoryAndOutstandingDebt() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-0000000001b1");
    wallet.openAccount(new OpenAccountCommand(userId, Money.krw(0L).currency()));
    adjustments.adjust(command("1b2", "1b5", 1L, userId, 10L));
    adjustments.adjust(command("1b3", "1b6", 2L, userId, 20L));
    adjustments.adjust(command("1b4", "1b7", 3L, userId, 30L));
    wallet.deposit(new DepositCommand(userId, Money.krw(10L), IdempotencyKey.of("deposit:queue")));

    assertThat(recovery.recoverOne()).isEqualTo(RecoveryWorker.Result.APPLIED);
    assertThat(integrity.findQueueDriftUsers()).isEmpty();

    jdbc.update("UPDATE account SET recovery_debt_amount = 51 WHERE user_id = ?", userId);
    assertThat(integrity.findQueueDriftUsers()).containsExactly(userId);
    jdbc.update("UPDATE account SET recovery_debt_amount = 50 WHERE user_id = ?", userId);
  }

  @Test
  void detectsOrphansWithoutOverflowingNumericDebt() {
    UUID bounded = UUID.fromString("019b76da-a000-7000-8000-0000000001b8");
    jdbc.update(
        """
        INSERT INTO account(user_id, available_currency, locked_currency, recovery_debt_amount,
          recovery_frozen_at, next_adjustment_sequence, created_at, updated_at)
        VALUES (?, 'KRW', 'KRW', 18446744073709551614, now(), 3, now(), now())
        """,
        bounded);
    insertBlocked("1b9", bounded, 1L, Long.MAX_VALUE);
    insertBlocked("1ba", bounded, 2L, Long.MAX_VALUE);
    assertThat(integrity.findQueueDriftUsers()).doesNotContain(bounded);

    UUID orphan = UUID.fromString("019b76da-a000-7000-8000-0000000001bb");
    insertBlocked("1bc", orphan, 1L, 10L);
    assertThat(integrity.findQueueDriftUsers()).contains(orphan);
    jdbc.update("DELETE FROM wallet_adjustment WHERE user_id IN (?, ?)", bounded, orphan);
    jdbc.update("DELETE FROM wallet_operation WHERE user_id IN (?, ?)", bounded, orphan);
    jdbc.update("DELETE FROM account WHERE user_id = ?", bounded);
  }

  private static AdjustmentCommand command(
      String revisionTail, String betTail, long revisionNumber, UUID userId, long amount) {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000" + revisionTail);
    return new AdjustmentCommand(
        revisionId,
        UUID.fromString("019b76da-a000-7000-8000-000000000" + betTail),
        revisionNumber,
        userId,
        Money.krw(amount),
        Money.krw(0L),
        IdempotencyKey.of("settlement:revision:" + revisionId));
  }

  private void insertBlocked(String revisionTail, UUID userId, long sequence, long amount) {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000" + revisionTail);
    String key = "settlement:revision:" + revisionId;
    jdbc.update(
        """
        INSERT INTO wallet_operation(idempotency_key, caller_id, operation_kind, user_id,
          request_amount, request_currency, request_fingerprint, status, requested_at, updated_at)
        VALUES (?, 'SETTLEMENT', 'BET_ADJUSTMENT', ?, ?, 'KRW', ?, 'BLOCKED_FUNDS', now(), now())
        """,
        key,
        userId,
        amount,
        "c".repeat(64));
    jdbc.update(
        """
        INSERT INTO wallet_adjustment(revision_id, idempotency_key, bet_id, revision_number,
          user_id, previous_payout_amount, new_payout_amount, delta_amount, currency, status,
          queue_sequence, queued_at, next_attempt_at, created_at, updated_at)
        VALUES (?, ?, ?, 1, ?, ?, 0, ?, 'KRW', 'BLOCKED', ?, now(), now(), now(), now())
        """,
        revisionId,
        key,
        UUID.randomUUID(),
        userId,
        amount,
        -amount,
        sequence);
  }
}
