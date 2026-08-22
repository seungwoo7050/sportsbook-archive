package com.sportsbook.settlement.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.EventLifecycleStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifecycleObservationTest {

  @Test
  void excludesDeliveryMetadataFromSemanticFingerprint() {
    UUID eventId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-08-22T00:00:00Z");
    Instant scheduledAt = Instant.parse("2026-08-23T00:00:00Z");

    LifecycleObservation first =
        LifecycleObservation.observe(
            eventId,
            EventLifecycleStatus.POSTPONED,
            occurredAt,
            scheduledAt,
            occurredAt.plusSeconds(1));
    LifecycleObservation replay =
        LifecycleObservation.observe(
            eventId,
            EventLifecycleStatus.POSTPONED,
            occurredAt,
            scheduledAt,
            occurredAt.plusSeconds(60));

    assertThat(replay.observationId()).isNotEqualTo(first.observationId());
    assertThat(replay.fingerprint()).isEqualTo(first.fingerprint());
  }

  @Test
  void fingerprintsEverySettlementRelevantField() {
    LifecycleFingerprinter fingerprints = new LifecycleFingerprinter();
    UUID eventId = UUID.randomUUID();
    Instant occurredAt = Instant.EPOCH;

    assertThat(fingerprints.fingerprint(eventId, EventLifecycleStatus.CANCELLED, occurredAt, null))
        .isNotEqualTo(
            fingerprints.fingerprint(eventId, EventLifecycleStatus.POSTPONED, occurredAt, null));
  }
}
