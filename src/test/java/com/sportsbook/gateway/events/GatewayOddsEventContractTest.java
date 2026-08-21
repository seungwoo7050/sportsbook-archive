package com.sportsbook.gateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.gateway.kafka.GatewayEventContractException;
import com.sportsbook.protocol.event.OddsChanged;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class GatewayOddsEventContractTest {

  @Test
  void acceptsCanonicalPayloadAndPartitionKeyIdentities() {
    OddsChanged event = oddsChanged();

    assertThat(GatewayEventContract.oddsChanged(record(event, bytes(event.getEventId()))))
        .isEqualTo(event);
  }

  @Test
  void rejectsMissingMismatchedAndMalformedKeys() {
    OddsChanged event = oddsChanged();

    assertRejected(record(event, null));
    assertRejected(record(event, bytes(UUID.randomUUID().toString())));
    assertRejected(record(event, new byte[] {(byte) 0xc3, (byte) 0x28}));
  }

  @Test
  void rejectsEveryNoncanonicalPayloadIdentity() {
    OddsChanged event = oddsChanged();

    assertInvalid(
        OddsChanged.newBuilder(event).setEventId(event.getEventId().toUpperCase()).build());
    assertInvalid(OddsChanged.newBuilder(event).setMarketId("not-a-uuid").build());
    assertInvalid(OddsChanged.newBuilder(event).setSelectionId("1-1-1-1-1").build());
  }

  private static void assertInvalid(OddsChanged event) {
    assertRejected(record(event, bytes(event.getEventId())));
  }

  private static void assertRejected(ConsumerRecord<byte[], byte[]> record) {
    assertThatThrownBy(() -> GatewayEventContract.oddsChanged(record))
        .isInstanceOf(GatewayEventContractException.class);
  }

  private static ConsumerRecord<byte[], byte[]> record(OddsChanged event, byte[] key) {
    return new ConsumerRecord<>("odds.changed", 0, 0, key, AvroTestSupport.encode(event));
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static OddsChanged oddsChanged() {
    return OddsChanged.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setMarketId(UUID.randomUUID().toString())
        .setSelectionId(UUID.randomUUID().toString())
        .setPreviousOdds("1.8500")
        .setNewOdds("1.9000")
        .setChangedAt(Instant.parse("2026-08-21T00:00:00Z"))
        .build();
  }
}
