package com.sportsbook.wallet.infrastructure.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void setsTheRfcVersionAndVariant() {
    UUID generated = UuidV7.generate();

    assertThat(generated.version()).isEqualTo(7);
    assertThat(generated.variant()).isEqualTo(2);
  }

  @Test
  void preservesMillisecondOrderingInTheUuidPrefix() {
    UUID earlier = UuidV7.generate(1_700_000_000_000L);
    UUID later = UuidV7.generate(1_700_000_000_001L);

    assertThat(earlier).isLessThan(later);
  }

  @Test
  void retainsTheSuppliedTimestamp() {
    long timestamp = 1_700_000_000_000L;

    UUID generated = UuidV7.generate(timestamp);

    assertThat(generated.getMostSignificantBits() >>> 16).isEqualTo(timestamp);
  }
}
