package com.sportsbook.protocol.value;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void factoryBuildsCanonicalKey() {
    IdempotencyKey key = IdempotencyKey.of("client-request-42");
    assertThat(key.value()).isEqualTo("client-request-42");
    assertThat(key).isEqualTo(new IdempotencyKey("client-request-42"));
  }

  @Test
  void maximumLengthIsAccepted() {
    IdempotencyKey key = IdempotencyKey.of("a".repeat(IdempotencyKey.MAX_LENGTH));
    assertThat(key.value()).hasSize(IdempotencyKey.MAX_LENGTH);
  }

  @Test
  void randomKeysAreDistinctUuidStrings() {
    IdempotencyKey first = IdempotencyKey.random();
    IdempotencyKey second = IdempotencyKey.random();
    assertThat(first).isNotEqualTo(second);
    assertThat(UUID.fromString(first.value()).toString()).isEqualTo(first.value());
  }

  @Test
  void jsonRoundTripsAsRawString() throws Exception {
    IdempotencyKey key = IdempotencyKey.of("client-request-42");
    assertThat(mapper.writeValueAsString(key)).isEqualTo("\"client-request-42\"");
    assertThat(mapper.readValue(mapper.writeValueAsString(key), IdempotencyKey.class))
        .isEqualTo(key);
  }
}
