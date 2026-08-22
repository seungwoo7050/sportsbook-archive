package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetTest {

  static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");
  static final String FINGERPRINT = "a".repeat(64);

  @Test
  void beginsPendingWithOwnedIdentity() {
    UUID betId = UUID.randomUUID();
    Bet bet = Bet.from(draft(betId, new BetSlipType.Single()));

    assertThat(bet.betId()).isEqualTo(betId);
    assertThat(bet.status()).isEqualTo(BetStatus.PENDING);
    assertThat(bet.idempotencyKey()).isEqualTo("request-1");
    assertThat(bet.createdAt()).isEqualTo(NOW);
  }

  static BetDraft draft(UUID betId, BetSlipType type) {
    return new BetDraft(
        betId,
        UUID.randomUUID(),
        "B-2026-08-22-00000000",
        type,
        Money.krw(1_000),
        Money.krw(2_000),
        IdempotencyKey.of("request-1"),
        FINGERPRINT,
        NOW);
  }
}
