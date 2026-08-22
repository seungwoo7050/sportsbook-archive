package com.sportsbook.admin.client;

import java.util.Objects;

public record SettlementRejectionPayload(String reason) {

  private static final int MAX_REASON_LENGTH = 256;

  public SettlementRejectionPayload {
    Objects.requireNonNull(reason, "reason");
    reason = reason.trim();
    if (reason.isEmpty()
        || reason.length() > MAX_REASON_LENGTH
        || reason.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Rejection reason must be 1 to 256 printable characters");
    }
  }
}
