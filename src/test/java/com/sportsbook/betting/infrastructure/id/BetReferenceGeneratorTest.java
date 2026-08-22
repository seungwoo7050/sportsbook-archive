package com.sportsbook.betting.infrastructure.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BetReferenceGeneratorTest {

  @Test
  void includesUtcDateAndBase36Suffix() {
    String reference = new BetReferenceGenerator().next(Instant.parse("2026-08-22T23:30:00Z"));

    assertThat(reference).matches("B-2026-08-22-[0-9A-Z]{8}");
  }
}
