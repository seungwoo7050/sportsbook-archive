package com.sportsbook.betting.placement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "wallet_event_receipt")
public class WalletEventReceipt {

  private static final Set<String> TOPICS = Set.of("wallet.debited.v1", "wallet.debit-failed.v1");

  @Id
  @Column(name = "event_id", nullable = false, updatable = false)
  private UUID eventId;

  @Column(name = "topic", nullable = false, updatable = false, length = 64)
  private String topic;

  @Column(name = "bet_id", nullable = false, updatable = false)
  private UUID betId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "payload_sha256", nullable = false, updatable = false, length = 64)
  private String payloadSha256;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  protected WalletEventReceipt() {}

  public static WalletEventReceipt pending(
      UUID eventId,
      String topic,
      UUID betId,
      UUID userId,
      String payloadSha256,
      Instant receivedAt) {
    if (!TOPICS.contains(topic)) {
      throw new IllegalArgumentException("Unsupported wallet event topic");
    }
    if (payloadSha256 == null || !payloadSha256.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("payloadSha256 must be lowercase SHA-256");
    }
    WalletEventReceipt receipt = new WalletEventReceipt();
    receipt.eventId = Objects.requireNonNull(eventId, "eventId");
    receipt.topic = topic;
    receipt.betId = Objects.requireNonNull(betId, "betId");
    receipt.userId = Objects.requireNonNull(userId, "userId");
    receipt.payloadSha256 = payloadSha256;
    receipt.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
    return receipt;
  }

  public void markProcessed(Instant at) {
    if (processedAt == null) {
      processedAt = Objects.requireNonNull(at, "at");
    }
  }

  public UUID eventId() {
    return eventId;
  }

  public String topic() {
    return topic;
  }

  public UUID betId() {
    return betId;
  }

  public UUID userId() {
    return userId;
  }

  public String payloadSha256() {
    return payloadSha256;
  }

  public Instant processedAt() {
    return processedAt;
  }
}
