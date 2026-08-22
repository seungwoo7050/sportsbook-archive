package com.sportsbook.admin.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository
    extends JpaRepository<AuditLogEntity, UUID>, JpaSpecificationExecutor<AuditLogEntity> {

  @Query(
      "SELECT audit FROM AuditLogEntity audit "
          + "WHERE audit.startedAt >= :from AND audit.startedAt < :to "
          + "AND (:actor IS NULL OR audit.actorId = :actor)")
  Page<AuditLogEntity> search(
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("actor") String actor,
      Pageable pageable);
}
