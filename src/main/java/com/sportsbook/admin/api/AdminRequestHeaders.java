package com.sportsbook.admin.api;

import com.sportsbook.protocol.value.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.UUID;

public final class AdminRequestHeaders {

  public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  private AdminRequestHeaders() {}

  public static IdempotencyKey requireIdempotencyKey(HttpServletRequest request) {
    String value = requireSingleValue(request);
    try {
      return IdempotencyKey.of(value);
    } catch (IllegalArgumentException invalid) {
      throw new AdminRequestException("Idempotency-Key is invalid", invalid);
    }
  }

  public static UUID requireUuidIdempotencyKey(HttpServletRequest request) {
    String value = requireSingleValue(request);
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException invalid) {
      throw new AdminRequestException("Idempotency-Key must be a UUID", invalid);
    }
  }

  private static String requireSingleValue(HttpServletRequest request) {
    Enumeration<String> values = request.getHeaders(IDEMPOTENCY_KEY);
    if (values == null || !values.hasMoreElements()) {
      throw new AdminRequestException("Exactly one Idempotency-Key header is required");
    }
    String value = values.nextElement();
    if (values.hasMoreElements()) {
      throw new AdminRequestException("Exactly one Idempotency-Key header is required");
    }
    return value;
  }
}
