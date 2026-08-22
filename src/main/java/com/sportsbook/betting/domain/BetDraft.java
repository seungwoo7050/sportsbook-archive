package com.sportsbook.betting.domain;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record BetDraft(
    UUID betId,
    UUID userId,
    String reference,
    BetSlipType slipType,
    Money stake,
    Money maxPayout,
    IdempotencyKey idempotencyKey,
    String requestFingerprint,
    Instant createdAt) {

  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

  public BetDraft {
    Objects.requireNonNull(betId, "betId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(slipType, "slipType");
    Objects.requireNonNull(stake, "stake");
    Objects.requireNonNull(maxPayout, "maxPayout");
    Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    Objects.requireNonNull(createdAt, "createdAt");
    if (reference == null || reference.isBlank()) {
      throw new IllegalArgumentException("reference must not be blank");
    }
    if (!SHA_256
        .matcher(Objects.requireNonNull(requestFingerprint, "requestFingerprint"))
        .matches()) {
      throw new IllegalArgumentException("requestFingerprint must be lowercase SHA-256");
    }
    if (stake.currency() != maxPayout.currency()) {
      throw new IllegalArgumentException("stake and max payout currencies differ");
    }
  }
}
