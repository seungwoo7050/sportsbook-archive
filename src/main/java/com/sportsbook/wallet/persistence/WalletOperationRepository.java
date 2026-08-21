package com.sportsbook.wallet.persistence;

import com.sportsbook.wallet.domain.WalletOperation;
import org.springframework.data.jpa.repository.JpaRepository;

/** Durable operation outcomes. Reads intentionally remain lock-free because rows are terminal. */
public interface WalletOperationRepository extends JpaRepository<WalletOperation, String> {}
