package com.sportsbook.wallet.service;

import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.LedgerSide;
import com.sportsbook.wallet.domain.SystemAccountIds;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Immutable success response rebuilt from the authoritative matched ledger pair. */
public record WalletOperationResult(
    UUID operationGroupId, UUID userId, Money amount, LedgerReason reason, Instant at) {

  public static WalletOperationResult fromExisting(List<LedgerEntry> pair) {
    if (pair.size() != 2) {
      throw new IllegalStateException(
          "Ledger result must contain exactly two entries (got " + pair.size() + ")");
    }

    LedgerEntry first = pair.get(0);
    Set<LedgerSide> sides = pair.stream().map(LedgerEntry::side).collect(Collectors.toSet());
    if (!sides.equals(EnumSet.allOf(LedgerSide.class))) {
      throw new IllegalStateException("Ledger result must contain one DEBIT and one CREDIT entry");
    }

    boolean sameTransfer =
        pair.stream()
            .allMatch(
                entry ->
                    entry.operationGroupId().equals(first.operationGroupId())
                        && entry.idempotencyKey().equals(first.idempotencyKey())
                        && entry.money().equals(first.money())
                        && entry.reason() == first.reason()
                        && entry.createdAt().equals(first.createdAt()));
    if (!sameTransfer) {
      throw new IllegalStateException("Ledger result entries do not describe one matched transfer");
    }

    Set<UUID> userIds =
        pair.stream()
            .map(LedgerEntry::accountId)
            .filter(accountId -> !SystemAccountIds.isSystemAccount(accountId))
            .collect(Collectors.toSet());
    if (userIds.size() != 1) {
      throw new IllegalStateException(
          "Ledger result must identify exactly one user account (got " + userIds.size() + ")");
    }

    return new WalletOperationResult(
        first.operationGroupId(),
        userIds.iterator().next(),
        first.money(),
        first.reason(),
        first.createdAt());
  }
}
