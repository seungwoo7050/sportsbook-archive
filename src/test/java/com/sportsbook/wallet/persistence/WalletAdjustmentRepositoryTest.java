package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.AdjustmentStatus;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
class WalletAdjustmentRepositoryTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000120");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired WalletAdjustmentRepository adjustments;
  @Autowired WalletOperationRepository operations;
  @Autowired AccountRepository accounts;
  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void persistsProofsAndLocksTheOldestBlockedHead() {
    AdjustmentCommand first = command("000000000121", "000000000131", 1L);
    AdjustmentCommand second = command("000000000122", "000000000132", 2L);
    operations.saveAll(java.util.List.of(blockedOperation(first), blockedOperation(second)));
    adjustments.saveAll(
        java.util.List.of(
            WalletAdjustment.blocked(first, 1L, NOW),
            WalletAdjustment.blocked(second, 2L, NOW.plusSeconds(1))));
    adjustments.flush();

    assertThat(adjustments.findByIdempotencyKey(second.idempotencyKey().value()))
        .get()
        .extracting(WalletAdjustment::status)
        .isEqualTo(AdjustmentStatus.BLOCKED);
    assertThat(adjustments.findByBetIdAndRevisionNumber(first.betId(), first.revisionNumber()))
        .isPresent();
    assertThat(adjustments.findOldestBlockedForUpdate(USER_ID))
        .get()
        .extracting(WalletAdjustment::revisionId)
        .isEqualTo(first.revisionId());
  }

  @Test
  void neverSelectsADueTailBehindABackedOffHead() {
    AdjustmentCommand first = command("000000000123", "000000000133", 1L);
    AdjustmentCommand second = command("000000000124", "000000000134", 2L);
    Account account = Account.openFor(USER_ID, Money.krw(0L).currency(), NOW);
    account.queueRecoveryDebt(first.absoluteDelta(), NOW);
    account.queueRecoveryDebt(second.absoluteDelta(), NOW.plusSeconds(1L));
    accounts.save(account);
    operations.saveAll(java.util.List.of(blockedOperation(first), blockedOperation(second)));
    WalletAdjustment head = WalletAdjustment.blocked(first, 1L, NOW);
    WalletAdjustment tail = WalletAdjustment.blocked(second, 2L, NOW.plusSeconds(1L));
    head.deferUntil(NOW.plusSeconds(2L), Instant.parse("2099-01-01T00:00:00Z"));
    adjustments.saveAll(java.util.List.of(head, tail));
    adjustments.flush();

    assertThat(accounts.lockNextDueRecoveryAccount()).isEmpty();

    jdbc.update(
        """
        UPDATE wallet_adjustment
        SET next_attempt_at=TIMESTAMPTZ '2026-01-01T00:00:00Z'
        WHERE revision_id=?
        """,
        head.revisionId());
    assertThat(accounts.lockNextDueRecoveryAccount())
        .get()
        .extracting(Account::userId)
        .isEqualTo(USER_ID);
    assertThat(operations.findByIdForUpdate(first.idempotencyKey().value()))
        .get()
        .extracting(WalletOperation::status)
        .isEqualTo(com.sportsbook.wallet.domain.WalletOperationStatus.BLOCKED_FUNDS);
  }

  private AdjustmentCommand command(String revisionTail, String betTail, long revisionNumber) {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-" + revisionTail);
    return new AdjustmentCommand(
        revisionId,
        UUID.fromString("019b76da-a000-7000-8000-" + betTail),
        revisionNumber,
        USER_ID,
        Money.krw(10L),
        Money.krw(5L),
        IdempotencyKey.of("settlement:revision:" + revisionId));
  }

  private WalletOperation blockedOperation(AdjustmentCommand command) {
    return WalletOperation.blockedFunds(
        command.idempotencyKey(),
        WalletCaller.SETTLEMENT,
        command.userId(),
        command.absoluteDelta(),
        "a".repeat(64),
        NOW);
  }
}
