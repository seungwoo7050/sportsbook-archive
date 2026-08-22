package com.sportsbook.settlement.outbox;

import com.sportsbook.settlement.infrastructure.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Durable raw event payload committed in the same transaction as settlement state. */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

  @Id
  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "topic", nullable = false, length = 64, updatable = false)
  private String topic;

  @Column(name = "partition_key", nullable = false, length = 64, updatable = false)
  private String partitionKey;

  @Column(name = "schema_name", nullable = false, length = 64, updatable = false)
  private String schemaName;

  @Column(name = "payload", nullable = false, updatable = false)
  private byte[] payload;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  protected OutboxEvent() {}

  private OutboxEvent(
      UUID eventId,
      String topic,
      String partitionKey,
      String schemaName,
      byte[] payload,
      Instant createdAt) {
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.topic = required(topic, "topic");
    this.partitionKey = required(partitionKey, "partitionKey");
    this.schemaName = required(schemaName, "schemaName");
    this.payload = Arrays.copyOf(Objects.requireNonNull(payload, "payload"), payload.length);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static OutboxEvent pending(
      String topic, String partitionKey, String schemaName, byte[] payload, Instant createdAt) {
    return new OutboxEvent(UuidV7.generate(), topic, partitionKey, schemaName, payload, createdAt);
  }

  public void markPublished(Instant when) {
    if (publishedAt == null) {
      publishedAt = Objects.requireNonNull(when, "when");
    }
  }

  public UUID eventId() {
    return eventId;
  }

  public String topic() {
    return topic;
  }

  public String partitionKey() {
    return partitionKey;
  }

  public byte[] payload() {
    return Arrays.copyOf(payload, payload.length);
  }

  public Instant publishedAt() {
    return publishedAt;
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}
