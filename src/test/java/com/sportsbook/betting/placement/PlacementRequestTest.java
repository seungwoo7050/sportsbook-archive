package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
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
}
