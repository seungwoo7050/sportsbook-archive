package com.sportsbook.betting.persistence;

import com.sportsbook.betting.domain.Bet;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BetRepository extends JpaRepository<Bet, UUID> {

  @EntityGraph(attributePaths = "legs")
  Optional<Bet> findByIdempotencyKey(String idempotencyKey);

  @EntityGraph(attributePaths = "legs")
  Optional<Bet> findWithLegsByBetId(UUID betId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = "legs")
  Optional<Bet> findLockedByBetId(UUID betId);

  @Transactional
  @Query(
      nativeQuery = true,
      value =
          """
          WITH candidates AS (
              SELECT b.bet_id
              FROM bet b
              WHERE b.status = 'PENDING'
                AND COALESCE(
                      b.reconciliation_eligible_at,
                      b.reconciliation_requested_at,
                      b.created_at + CAST(:pendingDelayMs AS bigint) * INTERVAL '1 millisecond'
                    ) <= CURRENT_TIMESTAMP
                AND (b.reconciliation_claim_until IS NULL
                     OR b.reconciliation_claim_until <= CURRENT_TIMESTAMP)
              ORDER BY COALESCE(
                         b.reconciliation_eligible_at,
                         b.reconciliation_requested_at,
                         b.created_at + CAST(:pendingDelayMs AS bigint) * INTERVAL '1 millisecond'
                       ), b.bet_id
              FOR UPDATE SKIP LOCKED
              LIMIT :batchSize
          ), claimed AS (
              UPDATE bet b
              SET reconciliation_claim_owner = :owner,
                  reconciliation_claim_until = CURRENT_TIMESTAMP
                      + CAST(:leaseMs AS bigint) * INTERVAL '1 millisecond',
                  reconciliation_eligible_at = CURRENT_TIMESTAMP
                      + CAST(:retryDelayMs AS bigint) * INTERVAL '1 millisecond'
              FROM candidates c
              WHERE b.bet_id = c.bet_id
              RETURNING b.bet_id
          )
          SELECT bet_id FROM claimed
          """)
  List<UUID> claimReconciliationBatch(
      @Param("owner") String owner,
      @Param("pendingDelayMs") long pendingDelayMs,
      @Param("leaseMs") long leaseMs,
      @Param("retryDelayMs") long retryDelayMs,
      @Param("batchSize") int batchSize);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          UPDATE bet
          SET reconciliation_claim_owner = NULL,
              reconciliation_claim_until = NULL
          WHERE bet_id = :betId
            AND reconciliation_claim_owner = :owner
          """)
  int clearReconciliationClaim(@Param("betId") UUID betId, @Param("owner") String owner);

  @EntityGraph(attributePaths = "legs")
  List<Bet> findByUserIdOrderByBetIdDesc(UUID userId, Pageable pageable);

  @EntityGraph(attributePaths = "legs")
  List<Bet> findByUserIdAndBetIdLessThanOrderByBetIdDesc(
      UUID userId, UUID cursor, Pageable pageable);
}
