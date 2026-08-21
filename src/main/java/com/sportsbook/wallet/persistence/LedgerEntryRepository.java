package com.sportsbook.wallet.persistence;

import com.sportsbook.wallet.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only ledger queries for durable operation replay and account history. */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

  List<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

  List<LedgerEntry> findByOperationGroupId(UUID operationGroupId);

  List<LedgerEntry> findByAccountIdOrderByCreatedAtAsc(UUID accountId);
}
