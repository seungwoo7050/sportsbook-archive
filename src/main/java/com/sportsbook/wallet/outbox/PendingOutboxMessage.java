package com.sportsbook.wallet.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PendingOutboxMessage(
    UUID eventId,
    String operationKey,
    String topic,
    String partitionKey,
    String schemaName,
    String deduplicationKey,
    byte[] payload,
    Instant createdAt) {

  public PendingOutboxMessage {
    eventId = Objects.requireNonNull(eventId, "eventId");
    operationKey = required(operationKey, "operationKey");
    topic = required(topic, "topic");
    partitionKey = required(partitionKey, "partitionKey");
    schemaName = required(schemaName, "schemaName");
    deduplicationKey = required(deduplicationKey, "deduplicationKey");
    payload = Objects.requireNonNull(payload, "payload").clone();
    if (payload.length == 0) {
      throw new IllegalArgumentException("payload must not be empty");
    }
    createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static PendingOutboxMessage create(
      String operationKey,
      String topic,
      String partitionKey,
      String schemaName,
      String deduplicationKey,
      byte[] payload,
      Instant now) {
    return new PendingOutboxMessage(
        UUID.randomUUID(),
        operationKey,
        topic,
        partitionKey,
        schemaName,
        deduplicationKey,
        payload,
        now);
  }

  @Override
  public byte[] payload() {
    return payload.clone();
  }

  private static String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
