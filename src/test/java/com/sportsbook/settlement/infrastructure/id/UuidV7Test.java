package com.sportsbook.settlement.infrastructure.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void encodesVersionVariantAndTimestampOrder() {
    UUID earlier = UuidV7.generate(1_000L);
    UUID later = UuidV7.generate(2_000L);

    assertThat(earlier.version()).isEqualTo(7);
    assertThat(earlier.variant()).isEqualTo(2);
    assertThat(Long.compareUnsigned(earlier.getMostSignificantBits(), later.getMostSignificantBits()))
        .isNegative();
  }
}
