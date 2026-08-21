package com.sportsbook.oddsfeed.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.event.MarketStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;

class OperatorActionCodecTest {

  private static final UUID ACTION_ID = UUID.randomUUID();
  private static final UUID EVENT_ID = UUID.randomUUID();
  private static final UUID MARKET_ID = UUID.randomUUID();
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-21T05:00:00Z");

  private final OperatorActionCodec codec = new OperatorActionCodec();

  @Test
  void decodesEveryPersistedField() {
    QueuedOperatorMarketAction queued = codec.decode(record(fields()), true);

    assertThat(queued.recordId()).isEqualTo(RecordId.of("1-0"));
    assertThat(queued.reclaimed()).isTrue();
    assertThat(queued.action().actionId()).isEqualTo(ACTION_ID);
    assertThat(queued.action().eventId().value()).isEqualTo(EVENT_ID);
    assertThat(queued.action().marketId().value()).isEqualTo(MARKET_ID);
    assertThat(queued.action().previousStatus()).isEqualTo(MarketStatus.OPEN);
    assertThat(queued.action().announcedStatus()).isEqualTo(MarketStatus.SUSPENDED);
    assertThat(queued.action().requestedStatus()).isEqualTo(MarketStatus.SUSPENDED);
    assertThat(queued.action().reason()).isEqualTo("incident");
    assertThat(queued.action().sequence()).isEqualTo(2);
    assertThat(queued.action().predecessor()).isEqualTo(1);
    assertThat(queued.action().occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void rejectsMissingRequiredFields() {
    Map<String, String> fields = new HashMap<>(fields());
    fields.remove("reason");

    assertThatThrownBy(() -> codec.decode(record(fields), false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("reason");
  }

  private static Map<String, String> fields() {
    return Map.of(
        "actionId", ACTION_ID.toString(),
        "eventId", EVENT_ID.toString(),
        "marketId", MARKET_ID.toString(),
        "previousStatus", "OPEN",
        "announcedStatus", "SUSPENDED",
        "requestedStatus", "SUSPENDED",
        "reason", "incident",
        "sequence", "2",
        "predecessor", "1",
        "occurredAt", Long.toString(OCCURRED_AT.toEpochMilli()));
  }

  private static MapRecord<String, String, String> record(Map<String, String> fields) {
    return StreamRecords.newRecord()
        .in("operator-actions")
        .withId(RecordId.of("1-0"))
        .ofMap(fields);
  }
}
