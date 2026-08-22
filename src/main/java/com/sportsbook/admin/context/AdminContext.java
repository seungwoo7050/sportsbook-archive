package com.sportsbook.admin.context;

import com.sportsbook.admin.security.AdminRole;
import java.util.Objects;
import java.util.UUID;

public record AdminContext(String actorId, AdminRole actorRole, UUID actionId, String traceId) {

  public AdminContext {
    if (actorId == null || actorId.isBlank()) {
      throw new IllegalArgumentException("actorId must not be blank");
    }
    Objects.requireNonNull(actorRole, "actorRole");
    Objects.requireNonNull(actionId, "actionId");
  }
}
