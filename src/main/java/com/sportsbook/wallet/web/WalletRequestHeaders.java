package com.sportsbook.wallet.web;

import com.sportsbook.protocol.value.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Objects;
import java.util.UUID;

/** Parses single-valued wallet request identity headers without normalization. */
public final class WalletRequestHeaders {
  public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  private WalletRequestHeaders() {}

  public static IdempotencyKey requireIdempotencyKey(HttpServletRequest request) {
    Objects.requireNonNull(request, "request");
    Enumeration<String> values = request.getHeaders(IDEMPOTENCY_KEY);
    if (values == null || !values.hasMoreElements()) {
      throw new IllegalArgumentException("Exactly one Idempotency-Key header is required");
    }
    String value = values.nextElement();
    if (values.hasMoreElements()) {
      throw new IllegalArgumentException("Exactly one Idempotency-Key header is required");
    }
    return IdempotencyKey.of(value);
  }

  public static IdempotencyKey requireCanonicalDebitKey(HttpServletRequest request) {
    IdempotencyKey key = requireIdempotencyKey(request);
    requireCanonicalDebitId(key.value());
    return key;
  }

  public static UUID requireCanonicalDebitId(String raw) {
    Objects.requireNonNull(raw, "raw");
    UUID parsed;
    try {
      parsed = UUID.fromString(raw);
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("Debit identity must be a canonical UUID");
    }
    if (!parsed.toString().equals(raw)) {
      throw new IllegalArgumentException("Debit identity must be a canonical UUID");
    }
    return parsed;
  }
}
