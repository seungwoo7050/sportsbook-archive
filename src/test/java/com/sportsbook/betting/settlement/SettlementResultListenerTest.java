package com.sportsbook.betting.settlement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sportsbook.betting.config.BettingTopics;
import com.sportsbook.betting.outbox.AvroSerializer;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.SettlementResultAvro;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class SettlementResultListenerTest {

  @Test
  void dispatchesStrictRevisionBytesWithTheBetKey() throws Exception {
    BetSettlementService settlement = mock(BetSettlementService.class);
    BetResolutionRevised event = revision();
    ConsumerRecord<String, byte[]> record = record(event, event.getBetId());

    new SettlementResultListener(settlement).onResolution(record);

    verify(settlement).apply(eq(event), anyString());
  }

  @Test
  void rejectsAKeyMismatchBeforeMutatingTheProjection() {
    BetSettlementService settlement = mock(BetSettlementService.class);
    BetResolutionRevised event = revision();
    ConsumerRecord<String, byte[]> record = record(event, UUID.randomUUID().toString());

    assertThatThrownBy(() -> new SettlementResultListener(settlement).onResolution(record))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Kafka key");
    verifyNoInteractions(settlement);
  }

  private static ConsumerRecord<String, byte[]> record(BetResolutionRevised event, String key) {
    return new ConsumerRecord<>(
        BettingTopics.BET_RESOLUTION_REVISED, 0, 0, key, AvroSerializer.serialize(event));
  }

  private static BetResolutionRevised revision() {
    return BetResolutionRevised.newBuilder()
        .setRevisionId(UUID.randomUUID().toString())
        .setRevisionNumber(1L)
        .setBetId(UUID.randomUUID().toString())
        .setUserId(UUID.randomUUID().toString())
        .setEventId(UUID.randomUUID().toString())
        .setPreviousResult(SettlementResultAvro.LOST)
        .setNewResult(SettlementResultAvro.WON)
        .setPreviousPayout(money(0))
        .setNewPayout(money(2_000))
        .setSourceResultSettledAt(Instant.EPOCH)
        .setRevisedAt(Instant.EPOCH.plusSeconds(1))
        .build();
  }

  private static com.sportsbook.protocol.event.Money money(long amount) {
    return com.sportsbook.protocol.event.Money.newBuilder()
        .setAmount(amount)
        .setCurrency("KRW")
        .build();
  }
}
