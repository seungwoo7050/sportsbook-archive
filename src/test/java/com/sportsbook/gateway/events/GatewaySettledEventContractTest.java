package com.sportsbook.gateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.gateway.kafka.GatewayEventContractException;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.SettlementResultAvro;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class GatewaySettledEventContractTest {

  @Test
  void acceptsCanonicalSettledPayloadAndEventKey() {
    BetSettled event = settled();

    assertThat(GatewayEventContract.betSettled(record(event, event.getEventId()))).isEqualTo(event);
  }

  @Test
  void rejectsMismatchedSettledEventKey() {
    assertRejected(settled(), UUID.randomUUID().toString());
  }

  @Test
  void rejectsNoncanonicalSettledBetUserAndEventIds() {
    BetSettled event = settled();

    assertRejected(BetSettled.newBuilder(event).setBetId("not-a-uuid").build());
    assertRejected(BetSettled.newBuilder(event).setUserId("NOT-A-UUID").build());
    assertRejected(BetSettled.newBuilder(event).setEventId("1-1-1-1-1").build());
  }

  private static void assertRejected(BetSettled event) {
    assertRejected(event, event.getEventId());
  }

  private static void assertRejected(BetSettled event, String key) {
    assertThatThrownBy(() -> GatewayEventContract.betSettled(record(event, key)))
        .isInstanceOf(GatewayEventContractException.class);
  }

  private static ConsumerRecord<byte[], byte[]> record(BetSettled event, String key) {
    return new ConsumerRecord<>(
        "bet.settled.v1",
        0,
        0,
        key.getBytes(StandardCharsets.UTF_8),
        AvroTestSupport.encode(event));
  }

  private static BetSettled settled() {
    Money stake = Money.newBuilder().setAmount(10_000).setCurrency("KRW").build();
    return BetSettled.newBuilder()
        .setBetId(UUID.randomUUID().toString())
        .setUserId(UUID.randomUUID().toString())
        .setEventId(UUID.randomUUID().toString())
        .setResult(SettlementResultAvro.WON)
        .setStake(stake)
        .setPayout(stake)
        .setSettledAt(Instant.parse("2026-08-21T00:00:01Z"))
        .build();
  }
}
