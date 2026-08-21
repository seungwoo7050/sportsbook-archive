package com.sportsbook.gateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.gateway.kafka.GatewayEventContractException;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.SettlementResultAvro;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class GatewayResolutionRevisionContractTest {

  @Test
  void acceptsCanonicalRevisionWithBetPartitionKey() {
    BetResolutionRevised event = revision();

    assertThat(GatewayEventContract.betResolutionRevised(record(event, event.getBetId())))
        .isEqualTo(event);
  }

  @Test
  void rejectsMismatchedBetPartitionKey() {
    assertRejected(revision(), UUID.randomUUID().toString());
  }

  @Test
  void rejectsNoncanonicalRevisionAndBetIdentities() {
    BetResolutionRevised event = revision();

    assertInvalid(BetResolutionRevised.newBuilder(event).setRevisionId("not-a-uuid").build());
    assertInvalid(BetResolutionRevised.newBuilder(event).setBetId("NOT-A-UUID").build());
    assertInvalid(BetResolutionRevised.newBuilder(event).setUserId("1-1-1-1-1").build());
    assertInvalid(BetResolutionRevised.newBuilder(event).setEventId("invalid").build());
  }

  @Test
  void rejectsInvalidRevisionSequenceAndSnapshots() {
    BetResolutionRevised event = revision();
    Money usd = Money.newBuilder().setAmount(20_000).setCurrency("USD").build();

    assertInvalid(BetResolutionRevised.newBuilder(event).setRevisionNumber(0).build());
    assertInvalid(BetResolutionRevised.newBuilder(event).setNewPayout(usd).build());
    assertInvalid(
        BetResolutionRevised.newBuilder(event)
            .setRevisedAt(event.getSourceResultSettledAt().minusSeconds(1))
            .build());
  }

  private static void assertInvalid(BetResolutionRevised event) {
    assertRejected(event, event.getBetId());
  }

  private static void assertRejected(BetResolutionRevised event, String key) {
    assertThatThrownBy(() -> GatewayEventContract.betResolutionRevised(record(event, key)))
        .isInstanceOf(GatewayEventContractException.class);
  }

  private static ConsumerRecord<byte[], byte[]> record(BetResolutionRevised event, String key) {
    return new ConsumerRecord<>(
        "bet.resolution.revised.v1",
        0,
        0,
        key.getBytes(StandardCharsets.UTF_8),
        AvroTestSupport.encode(event));
  }

  private static BetResolutionRevised revision() {
    Money previous = Money.newBuilder().setAmount(0).setCurrency("KRW").build();
    Money revised = Money.newBuilder().setAmount(20_000).setCurrency("KRW").build();
    return BetResolutionRevised.newBuilder()
        .setRevisionId(UUID.randomUUID().toString())
        .setRevisionNumber(1)
        .setBetId(UUID.randomUUID().toString())
        .setUserId(UUID.randomUUID().toString())
        .setEventId(UUID.randomUUID().toString())
        .setPreviousResult(SettlementResultAvro.LOST)
        .setNewResult(SettlementResultAvro.WON)
        .setPreviousPayout(previous)
        .setNewPayout(revised)
        .setSourceResultSettledAt(Instant.parse("2026-08-21T00:00:00Z"))
        .setRevisedAt(Instant.parse("2026-08-21T00:00:01Z"))
        .build();
  }
}
