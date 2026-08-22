package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.betting.persistence.WalletEventReceiptRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WalletEventInboxTest {

  @Test
  void savesReceiptBeforeMarkingTheBetForHttpReconciliation() {
    WalletEventReceiptRepository receipts = mock(WalletEventReceiptRepository.class);
    BetRepository bets = mock(BetRepository.class);
    Bet bet = mock(Bet.class);
    UUID eventId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    when(receipts.findById(eventId)).thenReturn(Optional.empty());
    when(bets.findLockedByBetId(betId)).thenReturn(Optional.of(bet));
    when(bet.userId()).thenReturn(userId);
    WalletEventInbox inbox = inbox(receipts, bets);

    WalletEventReceipt receipt =
        inbox.record(eventId, "wallet.debited.v1", betId, userId, "a".repeat(64));

    assertThat(receipt.payloadSha256()).isEqualTo("a".repeat(64));
    InOrder order = inOrder(receipts, bet);
    order.verify(receipts).saveAndFlush(receipt);
    order.verify(bet).requestReconciliation(Instant.EPOCH);
  }

  @Test
  void rejectsConflictingPayloadUnderTheSameEventId() {
    WalletEventReceiptRepository receipts = mock(WalletEventReceiptRepository.class);
    BetRepository bets = mock(BetRepository.class);
    UUID eventId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    WalletEventReceipt saved =
        WalletEventReceipt.pending(
            eventId, "wallet.debited.v1", betId, userId, "a".repeat(64), Instant.EPOCH);
    when(receipts.findById(eventId)).thenReturn(Optional.of(saved));

    assertThatThrownBy(
            () ->
                inbox(receipts, bets)
                    .record(eventId, "wallet.debited.v1", betId, userId, "b".repeat(64)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Conflicting");
    verifyNoInteractions(bets);
  }

  private static WalletEventInbox inbox(WalletEventReceiptRepository receipts, BetRepository bets) {
    return new WalletEventInbox(receipts, bets, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
  }
}
