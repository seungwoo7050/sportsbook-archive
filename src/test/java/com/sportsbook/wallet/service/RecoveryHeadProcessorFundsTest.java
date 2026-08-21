package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecoveryHeadProcessorFundsTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000183");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void underfundedHeadOnlyReceivesDurableBackoff() {
    RecoveryRetryPolicy retries = mock(RecoveryRetryPolicy.class);
    WalletTransferWriter transfers = mock(WalletTransferWriter.class);
    WalletAdjustmentRepository adjustments = mock(WalletAdjustmentRepository.class);
    WalletOperationRepository operations = mock(WalletOperationRepository.class);
    RecoveryHeadProcessor processor =
        new RecoveryHeadProcessor(retries, transfers, adjustments, operations);
    AdjustmentCommand command = command();
    Account account = Account.openFor(USER_ID, Money.krw(0L).currency(), NOW);
    account.queueRecoveryDebt(command.absoluteDelta(), NOW);
    account.increaseAvailable(Money.krw(100L), NOW);
    WalletAdjustment proof = WalletAdjustment.blocked(command, 1L, NOW);
    WalletOperation operation = blockedOperation(command);
    when(retries.retryAt(NOW.plusSeconds(2L), 0)).thenReturn(NOW.plusSeconds(3L));

    RecoveryHeadProcessor.Result result =
        processor.process(RecoveryClaim.locked(account, proof, operation), NOW.plusSeconds(2L));

    assertThat(result).isEqualTo(RecoveryHeadProcessor.Result.DEFERRED_FUNDS);
    assertThat(proof.retryCount()).isEqualTo(1);
    assertThat(proof.nextAttemptAt()).isEqualTo(NOW.plusSeconds(3L));
    assertThat(account.available()).isEqualTo(Money.krw(100L));
    assertThat(account.recoveryDebtAmount()).isEqualTo(BigInteger.valueOf(300L));
    verifyNoInteractions(transfers, adjustments, operations);
  }

  private AdjustmentCommand command() {
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000184");
    return new AdjustmentCommand(
        revisionId,
        UUID.fromString("019b76da-a000-7000-8000-000000000185"),
        1L,
        USER_ID,
        Money.krw(500L),
        Money.krw(200L),
        IdempotencyKey.of("settlement:revision:" + revisionId));
  }

  private WalletOperation blockedOperation(AdjustmentCommand command) {
    return WalletOperation.blockedFunds(
        command.idempotencyKey(),
        WalletCaller.SETTLEMENT,
        USER_ID,
        command.absoluteDelta(),
        "b".repeat(64),
        NOW);
  }
}
