package com.sportsbook.betting.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.config.BettingTopics;
import com.sportsbook.betting.config.PermanentKafkaException;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.outbox.AvroSerializer;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import com.sportsbook.protocol.event.SettlementResultAvro;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class SettlementResultListenerTest {

  @Test
  void dispatchesStrictRevisionBytesWithTheBetKey() throws Exception {
    BetSettlementService settlement = mock(BetSettlementService.class);
    BetResolutionRevised event = revision();
    ConsumerRecord<byte[], byte[]> record = record(event, event.getBetId());

    new SettlementResultListener(settlement, new SimpleMeterRegistry()).onResolution(record);

    verify(settlement).apply(eq(event), anyString());
  }

  @Test
  void rejectsAKeyMismatchBeforeMutatingTheProjection() {
    BetSettlementService settlement = mock(BetSettlementService.class);
    BetResolutionRevised event = revision();
    ConsumerRecord<byte[], byte[]> record = record(event, UUID.randomUUID().toString());

    assertThatThrownBy(
            () ->
                new SettlementResultListener(settlement, new SimpleMeterRegistry())
                    .onResolution(record))
        .isInstanceOf(PermanentKafkaException.class)
        .hasMessageContaining("Kafka key");
    verifyNoInteractions(settlement);
  }

  @Test
  void classifiesATombstoneAsPermanentBeforeHashing() {
    BetSettlementService settlement = mock(BetSettlementService.class);
    ConsumerRecord<byte[], byte[]> record =
        new ConsumerRecord<>(
            BettingTopics.BET_SETTLED,
            0,
            0,
            UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII),
            null);

    assertThatThrownBy(
            () ->
                new SettlementResultListener(settlement, new SimpleMeterRegistry())
                    .onResolution(record))
        .isInstanceOf(PermanentKafkaException.class)
        .hasMessageContaining("payload");
    verifyNoInteractions(settlement);
  }

  @Test
  void rejectsMarketVoidOnTheWholeSlipChannel() {
    BetSettlementService settlement = mock(BetSettlementService.class);
    BetVoided event =
        BetVoided.newBuilder()
            .setBetId(UUID.randomUUID().toString())
            .setUserId(UUID.randomUUID().toString())
            .setEventId(UUID.randomUUID().toString())
            .setReason(com.sportsbook.protocol.event.VoidReason.MARKET_VOID)
            .setRefund(money(1_000))
            .setVoidedAt(Instant.EPOCH)
            .build();

    assertThatThrownBy(
            () ->
                new SettlementResultListener(settlement, new SimpleMeterRegistry())
                    .onResolution(record(BettingTopics.BET_VOIDED, event, event.getEventId())))
        .isInstanceOf(PermanentKafkaException.class)
        .hasMessageContaining("settled VOID");
    verifyNoInteractions(settlement);
  }

  @Test
  void acceptsMarketVoidAsASettledVoidResult() throws Exception {
    BetSettlementService settlement = mock(BetSettlementService.class);
    BetSettled event =
        BetSettled.newBuilder()
            .setBetId(UUID.randomUUID().toString())
            .setUserId(UUID.randomUUID().toString())
            .setEventId(UUID.randomUUID().toString())
            .setResult(SettlementResultAvro.VOID)
            .setStake(money(1_000))
            .setPayout(money(1_000))
            .setSettledAt(Instant.EPOCH)
            .setResultDetail(java.util.Map.of("reason", "MARKET_VOID"))
            .build();

    new SettlementResultListener(settlement, new SimpleMeterRegistry())
        .onResolution(record(BettingTopics.BET_SETTLED, event, event.getEventId()));

    verify(settlement).apply(eq(event), anyString());
  }

  @Test
  void countsOnlyAppliedRevisionGaps() throws Exception {
    BetSettlementService settlement = mock(BetSettlementService.class);
    BetResolutionRevised event = revision();
    ConsumerRecord<byte[], byte[]> record = record(event, event.getBetId());
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    when(settlement.apply(eq(event), anyString()))
        .thenReturn(
            Bet.RevisionApplyResult.APPLIED,
            Bet.RevisionApplyResult.APPLIED_WITH_GAP,
            Bet.RevisionApplyResult.DUPLICATE);
    SettlementResultListener listener = new SettlementResultListener(settlement, meters);

    listener.onResolution(record);
    assertThat(meters.counter(SettlementResultListener.REVISION_GAP_METRIC).count()).isZero();
    listener.onResolution(record);
    listener.onResolution(record);

    assertThat(meters.counter(SettlementResultListener.REVISION_GAP_METRIC).count()).isEqualTo(1);
  }

  private static ConsumerRecord<byte[], byte[]> record(BetResolutionRevised event, String key) {
    return record(BettingTopics.BET_RESOLUTION_REVISED, event, key);
  }

  private static ConsumerRecord<byte[], byte[]> record(
      String topic, SpecificRecord event, String key) {
    return new ConsumerRecord<>(
        topic,
        0,
        0,
        key.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
        AvroSerializer.serialize(event));
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
