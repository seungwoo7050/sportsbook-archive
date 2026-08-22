package com.sportsbook.settlement.lifecycle;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.settlement.infrastructure.id.UuidV7;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LifecycleObservation(
    UUID observationId,
    UUID eventId,
    EventLifecycleStatus status,
    Instant occurredAt,
    Instant scheduledStartAt,
    Instant receivedAt,
    String fingerprint) {

  public LifecycleObservation {
    Objects.requireNonNull(observationId, "observationId");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(receivedAt, "receivedAt");
    if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Lifecycle fingerprint must be lowercase SHA-256");
    }
  }

  public static LifecycleObservation observe(
      UUID eventId,
      EventLifecycleStatus status,
      Instant occurredAt,
      Instant scheduledStartAt,
      Instant receivedAt) {
    String fingerprint =
        new LifecycleFingerprinter().fingerprint(eventId, status, occurredAt, scheduledStartAt);
    return new LifecycleObservation(
        UuidV7.generate(), eventId, status, occurredAt, scheduledStartAt, receivedAt, fingerprint);
  }
}
