package com.sportsbook.protocol.value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Caller-supplied idempotency key for mutating requests (ADR-0005).
 *
 * <p>The first request with a given key succeeds and its outcome is recorded; any retry with the
 * same key returns the same outcome rather than re-executing the side effect. The protocol layer
 * here only validates the key's wire shape — the actual dedup happens in each service via a unique
 * DB constraint (strong) plus a Redis cache (fast path), as described in ADR-0005.
 *
 * <p>Constraints:
 *
 * <ul>
 *   <li>Non-blank, length ≤ 128 — fits comfortably in an HTTP header and a typical varchar column.
 *   <li>Printable ASCII only ({@code 0x20–0x7E}) — avoids encoding ambiguity when the key crosses
 *       Kafka headers, HTTP headers, and DB columns.
 * </ul>
 *
 * <p>{@code toString} returns the raw value: idempotency keys are not secrets and showing them in
 * logs is desirable for tracing a retry across services.
 */
public record IdempotencyKey(@JsonValue String value) {

  public static final int MAX_LENGTH = 128;
  private static final Pattern ASCII_PRINTABLE = Pattern.compile("\\A[\\x20-\\x7E]+\\z");

  public IdempotencyKey {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("IdempotencyKey must not be blank");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "IdempotencyKey length " + value.length() + " exceeds max " + MAX_LENGTH);
    }
    if (!ASCII_PRINTABLE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "IdempotencyKey must contain only printable ASCII (0x20-0x7E)");
    }
  }

  @JsonCreator
  public static IdempotencyKey of(String value) {
    return new IdempotencyKey(value);
  }

  /** Generates a fresh key as a UUID v4 string. Useful for clients that don't supply one. */
  public static IdempotencyKey random() {
    return new IdempotencyKey(UUID.randomUUID().toString());
  }
}
