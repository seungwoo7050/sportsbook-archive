package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.LedgerSide;
import com.sportsbook.wallet.domain.SystemAccountIds;
import com.sportsbook.wallet.integrity.OperationCommitted;
import com.sportsbook.wallet.persistence.LedgerEntryRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class WalletTransferWriterTest {

  @Test
  @SuppressWarnings("unchecked")
  void persistsMatchedLegsBeforePublishingTheirGroup() {
    LedgerEntryRepository ledger = mock(LedgerEntryRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    WalletTransferWriter writer = new WalletTransferWriter(ledger, events);
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000020");
    IdempotencyKey key = IdempotencyKey.of("deposit:writer");
    Money amount = Money.krw(25L);
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    WalletOperationResult result =
        writer.write(
            new LedgerEntry.TransferLeg(userId, BalanceBucket.AVAILABLE),
            new LedgerEntry.TransferLeg(SystemAccountIds.EXTERNAL_PAYMENT, BalanceBucket.AVAILABLE),
            amount,
            LedgerReason.DEPOSIT,
            key,
            userId,
            now);

    ArgumentCaptor<Iterable<LedgerEntry>> entries = ArgumentCaptor.forClass(Iterable.class);
    ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
    var order = inOrder(ledger, events);
    order.verify(ledger).saveAll(entries.capture());
    order.verify(events).publishEvent(event.capture());
    var pair = StreamSupport.stream(entries.getValue().spliterator(), false).toList();

    assertThat(pair)
        .extracting(LedgerEntry::side)
        .containsExactly(LedgerSide.DEBIT, LedgerSide.CREDIT);
    assertThat(pair)
        .allSatisfy(
            entry -> {
              assertThat(entry.operationGroupId()).isEqualTo(result.operationGroupId());
              assertThat(entry.idempotencyKey()).isEqualTo(key.value());
              assertThat(entry.money()).isEqualTo(amount);
              assertThat(entry.reason()).isEqualTo(LedgerReason.DEPOSIT);
            });
    assertThat(pair)
        .extracting(LedgerEntry::accountId)
        .containsExactly(userId, SystemAccountIds.EXTERNAL_PAYMENT);
    assertThat(event.getValue()).isEqualTo(new OperationCommitted(result.operationGroupId()));
    assertThat(result)
        .isEqualTo(
            new WalletOperationResult(
                result.operationGroupId(), userId, amount, LedgerReason.DEPOSIT, now));
  }
}
