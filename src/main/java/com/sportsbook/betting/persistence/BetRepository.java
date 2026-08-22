package com.sportsbook.betting.persistence;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.protocol.domain.BetStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface BetRepository extends JpaRepository<Bet, UUID> {

  @EntityGraph(attributePaths = "legs")
  Optional<Bet> findByIdempotencyKey(String idempotencyKey);

  @EntityGraph(attributePaths = "legs")
  Optional<Bet> findWithLegsByBetId(UUID betId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = "legs")
  Optional<Bet> findLockedByBetId(UUID betId);

  @EntityGraph(attributePaths = "legs")
  List<Bet> findByStatusAndCreatedAtBefore(BetStatus status, Instant threshold, Pageable pageable);

  @EntityGraph(attributePaths = "legs")
  List<Bet> findByUserIdOrderByBetIdDesc(UUID userId, Pageable pageable);

  @EntityGraph(attributePaths = "legs")
  List<Bet> findByUserIdAndBetIdLessThanOrderByBetIdDesc(
      UUID userId, UUID cursor, Pageable pageable);
}
