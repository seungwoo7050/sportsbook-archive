package com.sportsbook.settlement.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  @Query(
      value =
          "select * from outbox_event where published_at is null "
              + "order by created_at, event_id for update skip locked limit :limit",
      nativeQuery = true)
  List<OutboxEvent> lockNextUnpublished(@Param("limit") int limit);
}
