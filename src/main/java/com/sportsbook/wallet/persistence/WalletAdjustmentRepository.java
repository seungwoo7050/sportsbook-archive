package com.sportsbook.wallet.persistence;

import com.sportsbook.wallet.domain.AdjustmentStatus;
import com.sportsbook.wallet.domain.WalletAdjustment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Durable correction proofs and account-scoped FIFO head selection. */
public interface WalletAdjustmentRepository extends JpaRepository<WalletAdjustment, UUID> {

  Optional<WalletAdjustment> findByIdempotencyKey(String idempotencyKey);

  Optional<WalletAdjustment> findByBetIdAndRevisionNumber(UUID betId, long revisionNumber);

  boolean existsByUserIdAndStatus(UUID userId, AdjustmentStatus status);

  @Query(
      value =
          """
          SELECT * FROM wallet_adjustment
          WHERE user_id = :userId AND status = 'BLOCKED'
          ORDER BY queue_sequence
          LIMIT 1
          FOR UPDATE
          """,
      nativeQuery = true)
  Optional<WalletAdjustment> findOldestBlockedForUpdate(@Param("userId") UUID userId);
}
