package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.security.AdminRole;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditLogEntityMappingTest {

  @Test
  void mapsAllLifecycleEvidenceWithoutCollapsingNullableFields() {
    UUID actionId = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    Instant startedAt = Instant.parse("2026-08-23T00:00:00Z");
    Instant completedAt = startedAt.plusSeconds(1);
    AuditLogEntity entity =
        new AuditLogEntity(
            actionId,
            "operator-1",
            AdminRole.ADMIN,
            "WALLET_REFUND",
            "user-1",
            AuditOutcome.SUCCESS,
            200,
            "approved",
            "trace-1",
            startedAt,
            completedAt);

    assertThat(entity.getActionId()).isEqualTo(actionId);
    assertThat(entity.getActorRole()).isEqualTo(AdminRole.ADMIN);
    assertThat(entity.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    assertThat(entity.getHttpStatus()).isEqualTo(200);
    assertThat(entity.getStartedAt()).isEqualTo(startedAt);
    assertThat(entity.getCompletedAt()).isEqualTo(completedAt);
    assertThat(AuditOutcome.STARTED.isTerminal()).isFalse();
    assertThat(AuditOutcome.UNKNOWN.isTerminal()).isTrue();
  }

  @Test
  void storesEnumsAsStringsAndUsesTheV2ColumnNames() throws Exception {
    assertThat(field("actorRole").getAnnotation(Enumerated.class).value())
        .isEqualTo(EnumType.STRING);
    assertThat(field("outcome").getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
    assertThat(field("startedAt").getAnnotation(Column.class).name()).isEqualTo("started_at");
    assertThat(field("completedAt").getAnnotation(Column.class).name()).isEqualTo("completed_at");
    assertThat(field("httpStatus").getAnnotation(Column.class).nullable()).isTrue();
    assertThat(field("completedAt").getAnnotation(Column.class).nullable()).isTrue();
  }

  private static Field field(String name) throws NoSuchFieldException {
    return AuditLogEntity.class.getDeclaredField(name);
  }
}
