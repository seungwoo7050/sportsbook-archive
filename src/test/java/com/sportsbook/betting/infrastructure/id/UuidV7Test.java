package com.sportsbook.betting.infrastructure.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void stampsVersionVariantAndTime() {
    long timestamp = 1_700_000_000_123L;
    UUID id = UuidV7.generate(timestamp);

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(timestamp);
  }
}
