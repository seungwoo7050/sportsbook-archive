package com.sportsbook.wallet.persistence;

import com.sportsbook.wallet.domain.WalletOperation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Durable outcomes with an explicit recovery lock for mutable blocked operations. */
public interface WalletOperationRepository extends JpaRepository<WalletOperation, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select operation from WalletOperation operation where operation.idempotencyKey = :key")
  Optional<WalletOperation> findByIdForUpdate(@Param("key") String key);
}
