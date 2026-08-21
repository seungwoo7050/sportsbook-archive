package com.sportsbook.wallet.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

  @Id
  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "operation_key", nullable = false, updatable = false)
  private String operationKey;

  @Column(nullable = false, updatable = false)
  private String topic;

  @Column(name = "partition_key", nullable = false, updatable = false)
  private String partitionKey;

  @Column(name = "schema_name", nullable = false, updatable = false)
  private String schemaName;

  @Column(name = "deduplication_key", nullable = false, updatable = false)
  private String deduplicationKey;

  @Column(name = "stream_sequence", nullable = false, updatable = false)
  private long streamSequence;

  @Column(nullable = false, updatable = false)
  private byte[] payload;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "available_at", nullable = false, insertable = false, updatable = false)
  private Instant availableAt;

  protected OutboxEvent() {}

  private OutboxEvent(PendingOutboxMessage message, long streamSequence) {
    if (streamSequence < 1L) {
      throw new IllegalArgumentException("streamSequence must be positive");
    }
    eventId = message.eventId();
    operationKey = message.operationKey();
    topic = message.topic();
    partitionKey = message.partitionKey();
    schemaName = message.schemaName();
    deduplicationKey = message.deduplicationKey();
    this.streamSequence = streamSequence;
    payload = message.payload();
    createdAt = message.createdAt();
  }

  public static OutboxEvent pending(PendingOutboxMessage message, long streamSequence) {
    return new OutboxEvent(message, streamSequence);
  }

  public UUID eventId() {
    return eventId;
  }

  public String operationKey() {
    return operationKey;
  }

  public String topic() {
    return topic;
  }

  public String partitionKey() {
    return partitionKey;
  }

  public String schemaName() {
    return schemaName;
  }

  public String deduplicationKey() {
    return deduplicationKey;
  }

  public long streamSequence() {
    return streamSequence;
  }

  public byte[] payload() {
    return payload.clone();
  }
}
