package com.sportsbook.oddsfeed.delivery;

import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.connection.stream.MapRecord;

/** Decodes durable operator Stream records into domain actions. */
final class OperatorActionCodec {

  QueuedOperatorMarketAction decode(MapRecord<String, String, String> record, boolean reclaimed) {
    Map<String, String> values = record.getValue();
    OperatorMarketAction action =
        new OperatorMarketAction(
            UUID.fromString(require(values, "actionId")),
            new EventId(UUID.fromString(require(values, "eventId"))),
            new MarketId(UUID.fromString(require(values, "marketId"))),
            MarketStatus.valueOf(require(values, "previousStatus")),
            MarketStatus.valueOf(require(values, "announcedStatus")),
            MarketStatus.valueOf(require(values, "requestedStatus")),
            require(values, "reason"),
            Long.parseLong(require(values, "sequence")),
            Long.parseLong(require(values, "predecessor")),
            Instant.ofEpochMilli(Long.parseLong(require(values, "occurredAt"))));
    return new QueuedOperatorMarketAction(record.getId(), action, reclaimed);
  }

  private static String require(Map<String, String> values, String field) {
    String value = values.get(field);
    if (value == null) {
      throw new IllegalStateException("Operator action is missing " + field);
    }
    return value;
  }
}
