package com.sportsbook.wallet.persistence;

import com.sportsbook.wallet.domain.Account;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Account storage. Every balance write enters through the pessimistic lock query. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from Account account where account.userId = :userId")
  Optional<Account> findByUserIdForUpdate(@Param("userId") UUID userId);

  @Query(
      value =
          """
          WITH db_clock AS (SELECT clock_timestamp() AS now)
          SELECT a.*
          FROM wallet_adjustment h
          JOIN account a ON a.user_id = h.user_id
          CROSS JOIN db_clock c
          WHERE h.status = 'BLOCKED'
            AND h.next_attempt_at <= c.now
            AND a.recovery_debt_amount > 0
            AND NOT EXISTS (
              SELECT 1 FROM wallet_adjustment older
              WHERE older.user_id = h.user_id
                AND older.status = 'BLOCKED'
                AND older.queue_sequence < h.queue_sequence
            )
          ORDER BY h.next_attempt_at, h.user_id
          FOR UPDATE OF a SKIP LOCKED
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<Account> lockNextDueRecoveryAccount();
}
