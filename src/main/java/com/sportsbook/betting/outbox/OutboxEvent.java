package com.sportsbook.betting.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

  @Id
  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "topic", nullable = false, updatable = false, length = 64)
  private String topic;

  @Column(name = "partition_key", nullable = false, updatable = false, length = 64)
  private String partitionKey;

  @Column(name = "schema_name", nullable = false, updatable = false, length = 64)
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
    this.topic = requireText(topic, "topic");
    this.partitionKey = requireText(partitionKey, "partitionKey");
    this.schemaName = requireText(schemaName, "schemaName");
    this.payload = Objects.requireNonNull(payload, "payload").clone();
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  public static OutboxEvent pending(
      UUID eventId,
      String topic,
      String partitionKey,
      String schemaName,
      byte[] payload,
      Instant createdAt) {
    return new OutboxEvent(eventId, topic, partitionKey, schemaName, payload, createdAt);
  }

  public void markPublished(Instant at) {
    if (publishedAt == null) {
      publishedAt = Objects.requireNonNull(at, "at");
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
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
    return payload.clone();
  }

  public Instant publishedAt() {
    return publishedAt;
  }
}
