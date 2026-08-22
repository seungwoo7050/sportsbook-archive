package com.sportsbook.settlement.event;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class KafkaUuidKeyValidatorTest {

  private final KafkaUuidKeyValidator validator = new KafkaUuidKeyValidator();

  @Test
  void acceptsOnlyAKeyMatchingTheRecordField() {
    UUID id = UUID.randomUUID();

    assertThat(validator.requireMatching(id.toString().getBytes(UTF_8), id.toString(), "userId"))
        .isEqualTo(id);
  }

  @Test
  void rejectsNoncanonicalMalformedAndMismatchedKeys() {
    UUID id = UUID.randomUUID();
    byte[] uppercase = id.toString().toUpperCase(java.util.Locale.ROOT).getBytes(UTF_8);
    byte[] malformed = id.toString().getBytes(UTF_8);
    malformed[0] = (byte) 0xff;

    assertInvalid(null, id.toString());
    assertInvalid(uppercase, id.toString());
    assertInvalid(malformed, id.toString());
    assertInvalid(id.toString().getBytes(UTF_8), UUID.randomUUID().toString());
  }

  private void assertInvalid(byte[] key, String field) {
    assertThatThrownBy(() -> validator.requireMatching(key, field, "userId"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Invalid Kafka userId:");
  }
}
