package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import com.sportsbook.wallet.domain.SystemAccountIds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletTransferTopologyTest {

  private static final UUID USER = UUID.fromString("019b76da-a000-7000-8000-000000000111");

  @Test
  void acceptsEveryFinalTransferTopology() {
    List<Topology> topologies =
        List.of(
            new Topology(
                LedgerReason.DEPOSIT,
                USER,
                BalanceBucket.AVAILABLE,
                SystemAccountIds.EXTERNAL_PAYMENT,
                BalanceBucket.AVAILABLE),
            new Topology(
                LedgerReason.WITHDRAW,
                SystemAccountIds.EXTERNAL_PAYMENT,
                BalanceBucket.AVAILABLE,
                USER,
                BalanceBucket.AVAILABLE),
            new Topology(
                LedgerReason.BET_DEBIT, USER, BalanceBucket.LOCKED, USER, BalanceBucket.AVAILABLE),
            new Topology(
                LedgerReason.BET_PAYOUT,
                USER,
                BalanceBucket.AVAILABLE,
                SystemAccountIds.HOUSE,
                BalanceBucket.AVAILABLE),
            new Topology(
                LedgerReason.BET_REFUND, USER, BalanceBucket.AVAILABLE, USER, BalanceBucket.LOCKED),
            new Topology(
                LedgerReason.BET_FORFEIT,
                SystemAccountIds.HOUSE,
                BalanceBucket.AVAILABLE,
                USER,
                BalanceBucket.LOCKED),
            new Topology(
                LedgerReason.BET_ADJUSTMENT,
                USER,
                BalanceBucket.AVAILABLE,
                SystemAccountIds.HOUSE,
                BalanceBucket.AVAILABLE),
            new Topology(
                LedgerReason.BET_ADJUSTMENT,
                SystemAccountIds.HOUSE,
                BalanceBucket.AVAILABLE,
                USER,
                BalanceBucket.AVAILABLE));

    assertThatCode(
            () ->
                topologies.forEach(
                    topology -> WalletOperationResult.fromExisting(topology.entries())))
        .doesNotThrowAnyException();
  }

  private record Topology(
      LedgerReason reason,
      UUID destination,
      BalanceBucket destinationBucket,
      UUID source,
      BalanceBucket sourceBucket) {

    private List<LedgerEntry> entries() {
      LedgerEntry.Pair pair =
          LedgerEntry.pair(
              new LedgerEntry.TransferLeg(destination, destinationBucket),
              new LedgerEntry.TransferLeg(source, sourceBucket),
              Money.krw(10_000L),
              reason,
              IdempotencyKey.of("topology:" + reason),
              UUID.fromString("019b76da-a000-7000-8000-000000000211"),
              Instant.parse("2026-07-14T00:00:00Z"));
      return List.of(pair.debit(), pair.credit());
    }
  }
}
