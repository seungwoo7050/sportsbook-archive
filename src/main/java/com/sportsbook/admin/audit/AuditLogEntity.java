package com.sportsbook.admin.audit;

import com.sportsbook.admin.security.AdminRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class AuditLogEntity {

  @Id
  @Column(name = "action_id", nullable = false)
  private UUID actionId;

  @Column(name = "actor_id", nullable = false, length = 128)
  private String actorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_role", nullable = false, length = 32)
  private AdminRole actorRole;

  @Column(name = "action", nullable = false, length = 64)
  private String action;

  @Column(name = "target", length = 256)
  private String target;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false, length = 16)
  private AuditOutcome outcome;

  @Column(name = "http_status")
  private Integer httpStatus;

  @Column(name = "reason", length = 512)
  private String reason;

  @Column(name = "trace_id", length = 64)
  private String traceId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;
}
