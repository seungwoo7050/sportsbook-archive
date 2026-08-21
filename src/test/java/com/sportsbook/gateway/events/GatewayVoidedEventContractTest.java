package com.sportsbook.gateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.gateway.kafka.GatewayEventContractException;
import com.sportsbook.protocol.event.BetVoided;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.VoidReason;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class GatewayVoidedEventContractTest {

  @Test
  void acceptsCanonicalVoidedPayloadAndEventKey() {
    BetVoided event = voided();

    assertThat(GatewayEventContract.betVoided(record(event, event.getEventId()))).isEqualTo(event);
  }

  @Test
  void rejectsMismatchedVoidedEventKey() {
    assertRejected(voided(), UUID.randomUUID().toString());
  }

  @Test
  void rejectsNoncanonicalVoidedBetUserAndEventIds() {
    BetVoided event = voided();

    assertRejected(BetVoided.newBuilder(event).setBetId("not-a-uuid").build());
    assertRejected(BetVoided.newBuilder(event).setUserId("NOT-A-UUID").build());
    assertRejected(BetVoided.newBuilder(event).setEventId("1-1-1-1-1").build());
  }

  private static void assertRejected(BetVoided event) {
    assertRejected(event, event.getEventId());
  }

  private static void assertRejected(BetVoided event, String key) {
    assertThatThrownBy(() -> GatewayEventContract.betVoided(record(event, key)))
        .isInstanceOf(GatewayEventContractException.class);
  }

  private static ConsumerRecord<byte[], byte[]> record(BetVoided event, String key) {
    return new ConsumerRecord<>(
        "bet.voided.v1", 0, 0, key.getBytes(StandardCharsets.UTF_8), AvroTestSupport.encode(event));
  }

  private static BetVoided voided() {
    Money refund = Money.newBuilder().setAmount(10_000).setCurrency("KRW").build();
    return BetVoided.newBuilder()
        .setBetId(UUID.randomUUID().toString())
        .setUserId(UUID.randomUUID().toString())
        .setEventId(UUID.randomUUID().toString())
        .setReason(VoidReason.EVENT_POSTPONED)
        .setRefund(refund)
        .setVoidedAt(Instant.parse("2026-08-21T00:00:01Z"))
        .build();
  }
}
