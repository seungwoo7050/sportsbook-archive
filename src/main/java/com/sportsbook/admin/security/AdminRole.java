package com.sportsbook.admin.security;

import java.util.Optional;

public enum AdminRole {
  ADMIN,
  TRADER,
  CS,
  READONLY;

  public String authority() {
    return "ROLE_" + name();
  }

  public static Optional<AdminRole> fromClaim(Object claim) {
    if (!(claim instanceof String value)) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(value));
    } catch (IllegalArgumentException unknownRole) {
      return Optional.empty();
    }
  }
}
