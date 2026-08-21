package com.sportsbook.wallet.outbox;

import java.time.Instant;
import java.util.Objects;

public record LeasedOutboxMessage(
    OutboxLease lease,
    String topic,
    String partitionKey,
    String schemaName,
    byte[] payload,
    long streamSequence,
    boolean leaseTakeover,
    int attemptCount,
    Instant createdAt) {

  public LeasedOutboxMessage {
    lease = Objects.requireNonNull(lease, "lease");
    topic = required(topic, "topic");
    partitionKey = required(partitionKey, "partitionKey");
    schemaName = required(schemaName, "schemaName");
    payload = Objects.requireNonNull(payload, "payload").clone();
    if (payload.length == 0) {
      throw new IllegalArgumentException("payload must not be empty");
    }
    if (streamSequence < 1L) {
      throw new IllegalArgumentException("streamSequence must be positive");
    }
    if (attemptCount < 1) {
      throw new IllegalArgumentException("attemptCount must be positive");
    }
    createdAt = Objects.requireNonNull(createdAt, "createdAt");
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
