package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.error.ErrorCode;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlacementRequestTest {

  @Test
  void mapsOneAuthoritativeIdempotencyNamespace() throws Exception {
    Field key = PlacementRequest.class.getDeclaredField("idempotencyKey");
    Field outcome = PlacementRequest.class.getDeclaredField("outcome");
    Field fingerprint = PlacementRequest.class.getDeclaredField("requestFingerprint");

    assertThat(key.getAnnotation(Column.class).name()).isEqualTo("idempotency_key");
    assertThat(outcome.getAnnotation(Column.class).name()).isEqualTo("outcome");
    assertThat(fingerprint.getAnnotation(Column.class).length()).isEqualTo(64);
  }

  @Test
  void retainsDefinitivePreflightVerdict() {
    PlacementRequest request =
        PlacementRequest.rejected(
            "request-1",
            UUID.randomUUID(),
            "a".repeat(64),
            ErrorCode.VALIDATION_FAILED,
            "invalid slip",
            Instant.EPOCH);

    assertThat(request.outcome()).isEqualTo(PlacementOutcome.REJECTION);
    assertThat(request.betId()).isNull();
    assertThat(request.errorCode()).isEqualTo("VALIDATION_FAILED");
    assertThat(request.errorDetail()).isEqualTo("invalid slip");
  }
}
