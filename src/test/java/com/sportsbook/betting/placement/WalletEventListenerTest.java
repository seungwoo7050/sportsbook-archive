package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sportsbook.betting.config.BettingTopics;
import com.sportsbook.betting.config.PermanentKafkaException;
import com.sportsbook.betting.outbox.AvroSerializer;
import com.sportsbook.protocol.event.WalletDebited;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WalletEventListenerTest {

  @Test
  void checkpointsThenReconcilesBeforeAcknowledgementReturns() throws Exception {
    WalletEventInbox inbox = mock(WalletEventInbox.class);
    BetPlacementService placement = mock(BetPlacementService.class);
    UUID eventId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    WalletDebited event =
        WalletDebited.newBuilder()
            .setUserId(userId.toString())
            .setAmount(
                com.sportsbook.protocol.event.Money.newBuilder()
                    .setAmount(1_000L)
                    .setCurrency("KRW")
                    .build())
            .setIdempotencyKey(betId.toString())
            .setLedgerTxId(UUID.randomUUID().toString())
            .setOccurredAt(Instant.EPOCH)
            .build();
    ConsumerRecord<byte[], byte[]> record =
        new ConsumerRecord<>(
            BettingTopics.WALLET_DEBITED,
            2,
            7,
            userId.toString().getBytes(StandardCharsets.US_ASCII),
            AvroSerializer.serialize(event));
    record.headers().add("event-id", eventId.toString().getBytes(StandardCharsets.US_ASCII));

    new WalletEventListener(inbox, placement).onWalletEvent(record);

    InOrder order = inOrder(inbox, placement);
    order
        .verify(inbox)
        .record(eq(eventId), eq(BettingTopics.WALLET_DEBITED), eq(betId), eq(userId), anyString());
    order.verify(placement).reconcile(betId);
    order.verify(inbox).markProcessed(eventId);
  }

  @Test
  void classifiesANullEventIdHeaderAsPermanent() {
    WalletEventInbox inbox = mock(WalletEventInbox.class);
    BetPlacementService placement = mock(BetPlacementService.class);
    UUID userId = UUID.randomUUID();
    WalletDebited event =
        WalletDebited.newBuilder()
            .setUserId(userId.toString())
            .setAmount(
                com.sportsbook.protocol.event.Money.newBuilder()
                    .setAmount(1_000L)
                    .setCurrency("KRW")
                    .build())
            .setIdempotencyKey(UUID.randomUUID().toString())
            .setLedgerTxId(UUID.randomUUID().toString())
            .setOccurredAt(Instant.EPOCH)
            .build();
    ConsumerRecord<byte[], byte[]> record =
        new ConsumerRecord<>(
            BettingTopics.WALLET_DEBITED,
            0,
            0,
            userId.toString().getBytes(StandardCharsets.US_ASCII),
            AvroSerializer.serialize(event));
    record.headers().add("event-id", null);

    assertThatThrownBy(() -> new WalletEventListener(inbox, placement).onWalletEvent(record))
        .isInstanceOf(PermanentKafkaException.class)
        .hasMessageContaining("header value");
    verifyNoInteractions(inbox, placement);
  }
}
