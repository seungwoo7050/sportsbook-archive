package com.sportsbook.betting.persistence;

import com.sportsbook.betting.placement.WalletEventReceipt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletEventReceiptRepository extends JpaRepository<WalletEventReceipt, UUID> {}
