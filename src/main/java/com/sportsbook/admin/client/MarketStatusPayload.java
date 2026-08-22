package com.sportsbook.admin.client;

import java.util.Objects;

public record MarketStatusPayload(String reason) {

  public static final int MAX_REASON_LENGTH = 256;

  public MarketStatusPayload {
    Objects.requireNonNull(reason, "reason");
    reason = reason.trim();
    if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH) {
      throw new IllegalArgumentException("Market status reason must contain 1 to 256 characters");
    }
  }
}
