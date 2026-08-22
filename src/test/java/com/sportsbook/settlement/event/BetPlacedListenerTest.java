package com.sportsbook.settlement.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.RequestedSelection;
import com.sportsbook.settlement.readmodel.BetReadModelWriter;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.kafka.support.Acknowledgment;

class BetPlacedListenerTest {

  private final BetReadModelWriter writer = mock(BetReadModelWriter.class);
  private final BetPlacedListener listener = new BetPlacedListener(writer);
  private final Acknowledgment acknowledgment = mock(Acknowledgment.class);

  @Test
  void acknowledgesOnlyAfterTheWriterReturns() {
    BetPlacedRequested event = event();
    ConsumerRecord<byte[], byte[]> record = record(event, event.getUserId().toString());

    listener.receive(record, acknowledgment);

    InOrder committed = inOrder(writer, acknowledgment);
    committed.verify(writer).record(any());
    committed.verify(acknowledgment).acknowledge();

    reset(writer, acknowledgment);
    when(writer.record(any())).thenThrow(new IllegalStateException("database unavailable"));
    assertThatThrownBy(() -> listener.receive(record, acknowledgment))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(acknowledgment);
  }

  @Test
  void rejectsAMismatchedRawUserKeyBeforePersistence() {
    BetPlacedRequested event = event();

    assertThatThrownBy(
            () -> listener.receive(record(event, UUID.randomUUID().toString()), acknowledgment))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(writer, acknowledgment);
  }

  private static ConsumerRecord<byte[], byte[]> record(BetPlacedRequested event, String key) {
    return new ConsumerRecord<>("bet.placed.v1", 0, 0, key.getBytes(UTF_8), encode(event));
  }

  private static BetPlacedRequested event() {
    RequestedSelection selected =
        RequestedSelection.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setMarketId(UUID.randomUUID().toString())
            .setSelectionId(UUID.randomUUID().toString())
            .setOddsAtSubmission("2.0000")
            .build();
    return BetPlacedRequested.newBuilder()
        .setBetId(UUID.randomUUID().toString())
        .setUserId(UUID.randomUUID().toString())
        .setSlipType(BetSlipTypeTag.SINGLE)
        .setSystemMinWins(null)
        .setSystemTotalSelections(null)
        .setSelections(List.of(selected))
        .setStake(Money.newBuilder().setAmount(100).setCurrency("KRW").build())
        .setIdempotencyKey("placement-key")
        .setRequestedAt(Instant.EPOCH)
        .build();
  }

  private static byte[] encode(BetPlacedRequested event) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      var encoder = EncoderFactory.get().binaryEncoder(output, null);
      new SpecificDatumWriter<BetPlacedRequested>(event.getSchema()).write(event, encoder);
      encoder.flush();
      return output.toByteArray();
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
