package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.AdjustmentStatus;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.persistence.AccountRepository;
import com.sportsbook.wallet.persistence.LedgerEntryRepository;
import com.sportsbook.wallet.persistence.OutboxEventRepository;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@Import({AdjustmentProofWriter.class, WalletTransferWriter.class})
class AdjustmentProofWriterTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000121");
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-000000000122");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired AdjustmentProofWriter writer;
  @Autowired AccountRepository accounts;
  @Autowired WalletAdjustmentRepository adjustments;
  @Autowired WalletOperationRepository operations;
  @Autowired LedgerEntryRepository ledger;
  @Autowired OutboxEventRepository outbox;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void commitsAnIncreaseWithOneLedgerPairAndNoWalletEvent() {
    accounts.saveAndFlush(Account.openFor(USER_ID, Currency.KRW, NOW));
    AdjustmentCommand command = command(700L, 1_000L);
    Account account = accounts.findByUserIdForUpdate(USER_ID).orElseThrow();

    WalletOperation operation =
        writer.applyIncrease(command, "a".repeat(64), account, Optional.empty(), NOW);
    operations.saveAndFlush(operation);
    adjustments.flush();

    assertThat(account.available()).isEqualTo(Money.krw(300L));
    assertThat(ledger.findByIdempotencyKey(command.idempotencyKey().value())).hasSize(2);
    assertThat(outbox.count()).isZero();
    assertThat(adjustments.findById(REVISION_ID))
        .get()
        .satisfies(
            proof -> {
              assertThat(proof.status()).isEqualTo(AdjustmentStatus.APPLIED);
              assertThat(proof.operationGroupId()).isEqualTo(operation.operationGroupId());
            });
  }

  private AdjustmentCommand command(long previous, long next) {
    return new AdjustmentCommand(
        REVISION_ID,
        UUID.fromString("019b76da-a000-7000-8000-000000000123"),
        1L,
        USER_ID,
        Money.krw(previous),
        Money.krw(next),
        IdempotencyKey.of("settlement:revision:" + REVISION_ID));
  }
}
