package com.sportsbook.betting.placement;

import com.sportsbook.betting.config.PermanentKafkaException;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.betting.persistence.WalletEventReceiptRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WalletEventInbox {

  private final WalletEventReceiptRepository receipts;
  private final BetRepository bets;
  private final Clock clock;

  public WalletEventInbox(WalletEventReceiptRepository receipts, BetRepository bets, Clock clock) {
    this.receipts = receipts;
    this.bets = bets;
    this.clock = clock;
  }

  @Transactional
  public WalletEventReceipt record(
      UUID eventId, String topic, UUID betId, UUID userId, String payloadHash) {
    WalletEventReceipt existing = receipts.findById(eventId).orElse(null);
    if (existing != null) {
      if (existing.topic().equals(topic)
          && existing.betId().equals(betId)
          && existing.userId().equals(userId)
          && existing.payloadSha256().equals(payloadHash)) {
        return existing;
      }
      throw new PermanentKafkaException("Conflicting wallet event replay: " + eventId);
    }
    Bet bet =
        bets.findLockedByBetId(betId)
            .orElseThrow(() -> new PermanentKafkaException("Wallet event references unknown bet"));
    if (!bet.userId().equals(userId)) {
      throw new PermanentKafkaException("Wallet event actor does not own bet");
    }
    Instant now = clock.instant();
    WalletEventReceipt receipt =
        WalletEventReceipt.pending(eventId, topic, betId, userId, payloadHash, now);
    receipts.saveAndFlush(receipt);
    bet.requestReconciliation(now);
    return receipt;
  }

  @Transactional
  public void markProcessed(UUID eventId) {
    WalletEventReceipt receipt =
        receipts
            .findById(eventId)
            .orElseThrow(() -> new IllegalStateException("Wallet receipt disappeared"));
    receipt.markProcessed(clock.instant());
  }
}
