package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.infrastructure.id.UuidV7;
import com.sportsbook.wallet.integrity.OperationCommitted;
import com.sportsbook.wallet.persistence.LedgerEntryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Appends one complete double-entry transfer and announces its transaction-bound group. */
@Component
public class WalletTransferWriter {

  private final LedgerEntryRepository ledger;
  private final ApplicationEventPublisher events;

  public WalletTransferWriter(LedgerEntryRepository ledger, ApplicationEventPublisher events) {
    this.ledger = ledger;
    this.events = events;
  }

  @SuppressWarnings("checkstyle:ParameterNumber")
  public WalletOperationResult write(
      LedgerEntry.TransferLeg destination,
      LedgerEntry.TransferLeg source,
      Money amount,
      LedgerReason reason,
      IdempotencyKey key,
      UUID userId,
      Instant now) {
    return writeReceipt(destination, source, amount, reason, key, userId, now).result();
  }

  @SuppressWarnings("checkstyle:ParameterNumber")
  public WalletTransferReceipt writeReceipt(
      LedgerEntry.TransferLeg destination,
      LedgerEntry.TransferLeg source,
      Money amount,
      LedgerReason reason,
      IdempotencyKey key,
      UUID userId,
      Instant now) {
    UUID groupId = UuidV7.generate();
    LedgerEntry.Pair pair =
        LedgerEntry.pair(destination, source, amount, reason, key, groupId, now);
    ledger.saveAll(List.of(pair.debit(), pair.credit()));
    events.publishEvent(new OperationCommitted(groupId));
    WalletOperationResult result = new WalletOperationResult(groupId, userId, amount, reason, now);
    return new WalletTransferReceipt(result, pair.debit().entryId(), pair.credit().entryId());
  }
}
