package com.sportsbook.admin.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.admin.security.AdminRole;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminContextIdentityTest {

  @Test
  void generatesUniqueRfc9562Version7ActionIds() {
    Set<UUID> ids = new HashSet<>();

    for (int index = 0; index < 1_000; index++) {
      UUID id = Uuid7.generate();
      assertThat(id.version()).isEqualTo(7);
      assertThat(id.variant()).isEqualTo(2);
      ids.add(id);
    }

    assertThat(ids).hasSize(1_000);
  }

  @Test
  void embedsTheCurrentUnixMillisecondTimestamp() {
    long before = System.currentTimeMillis();
    UUID id = Uuid7.generate();
    long after = System.currentTimeMillis();
    long embeddedTimestamp = id.getMostSignificantBits() >>> 16;

    assertThat(embeddedTimestamp).isBetween(before, after);
  }

  @Test
  void requiresACompleteAuthenticatedIdentity() {
    UUID actionId = Uuid7.generate();
    AdminContext context = new AdminContext("operator-1", AdminRole.ADMIN, actionId, "trace-1");

    assertThat(context.actorId()).isEqualTo("operator-1");
    assertThat(context.actorRole()).isEqualTo(AdminRole.ADMIN);
    assertThat(context.actionId()).isEqualTo(actionId);
    assertThat(context.traceId()).isEqualTo("trace-1");
    assertThatThrownBy(() -> new AdminContext(" ", AdminRole.ADMIN, actionId, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AdminContext("operator-1", null, actionId, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new AdminContext("operator-1", AdminRole.ADMIN, null, null))
        .isInstanceOf(NullPointerException.class);
  }
}
