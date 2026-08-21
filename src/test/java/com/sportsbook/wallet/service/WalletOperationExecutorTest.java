package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.WalletOperation;
import com.sportsbook.wallet.domain.WalletOperationKind;
import com.sportsbook.wallet.persistence.IdempotencyKeyLock;
import com.sportsbook.wallet.persistence.WalletOperationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class WalletOperationExecutorTest {
  @Mock WalletOperationRepository operations;
  @Mock IdempotencyKeyLock keyLocks;
  @Mock TransactionTemplate writeTransaction;
  @InjectMocks WalletOperationExecutor executor;

  @Test
  void replaysTheDurableOutcomeBeforeTakingTheAdvisoryLock() {
    IdempotencyKey key = IdempotencyKey.of("operation:durable-replay");
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000020");
    Money amount = Money.krw(50L);
    WalletRequestIdentity request =
        new WalletRequestIdentity(
            key, WalletCaller.PLATFORM, WalletOperationKind.DEPOSIT, userId, amount);
    WalletOperation outcome =
        WalletOperation.succeeded(
            key,
            request.caller(),
            request.kind(),
            request.userId(),
            request.amount(),
            request.fingerprint(),
            UUID.fromString("019b76da-a000-7000-8000-000000000021"),
            Instant.parse("2026-01-03T00:00:00Z"));
    @SuppressWarnings("unchecked")
    Function<String, WalletOperation> firstWriter = mock(Function.class);
    when(operations.findById(key.value())).thenReturn(Optional.of(outcome));

    assertThat(executor.execute(key, request.caller(), request.kind(), userId, amount, firstWriter))
        .isSameAs(outcome);

    verify(keyLocks, never()).acquire(key);
    verify(writeTransaction, never()).execute(org.mockito.ArgumentMatchers.any());
    verify(firstWriter, never()).apply(org.mockito.ArgumentMatchers.anyString());
  }
}
