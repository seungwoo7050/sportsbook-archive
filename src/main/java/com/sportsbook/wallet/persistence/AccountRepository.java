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
}
