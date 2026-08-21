package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.wallet.domain.Account;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.persistence.DatabaseClock;
import com.sportsbook.wallet.persistence.WalletAdjustmentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecoveryWakeServiceTest {
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-000000000160");
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Mock WalletAdjustmentRepository adjustments;
  @Mock DatabaseClock databaseClock;
  @Mock Account account;
  @Mock WalletAdjustment head;
  @InjectMocks RecoveryWakeService service;

  @Test
  void skipsUnfrozenAccountsWithoutLockingAProof() {
    service.wake(account);

    verifyNoInteractions(adjustments, databaseClock);
  }

  @Test
  void locksAndWakesOnlyTheOldestBlockedProof() {
    when(account.isOutboundFrozen()).thenReturn(true);
    when(account.userId()).thenReturn(USER_ID);
    when(adjustments.findOldestBlockedForUpdate(USER_ID)).thenReturn(Optional.of(head));
    when(databaseClock.now()).thenReturn(NOW);

    service.wake(account);

    var order = inOrder(adjustments, databaseClock, head);
    order.verify(adjustments).findOldestBlockedForUpdate(USER_ID);
    order.verify(databaseClock).now();
    order.verify(head).wake(NOW);
  }

  @Test
  void failsClosedWhenFrozenDebtHasNoHead() {
    when(account.isOutboundFrozen()).thenReturn(true);
    when(account.userId()).thenReturn(USER_ID);
    when(adjustments.findOldestBlockedForUpdate(USER_ID)).thenReturn(Optional.empty());

    assertThatIllegalStateException()
        .isThrownBy(() -> service.wake(account))
        .withMessageContaining("no FIFO head");
  }
}
