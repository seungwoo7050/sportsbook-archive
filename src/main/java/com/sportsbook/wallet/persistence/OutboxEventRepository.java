package com.sportsbook.wallet.persistence;

import com.sportsbook.wallet.outbox.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findAllByOrderByTopicAscPartitionKeyAscStreamSequenceAsc();
}
