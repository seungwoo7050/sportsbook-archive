package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetDraftTest {

  @Test
  void rejectsNonCanonicalFingerprint() {
    assertThatThrownBy(
            () ->
                new BetDraft(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "B-2026-08-22-00000000",
                    new BetSlipType.Single(),
                    Money.krw(1_000),
                    Money.krw(2_000),
                    IdempotencyKey.of("request-1"),
                    "ABC",
                    Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SHA-256");
  }
}
