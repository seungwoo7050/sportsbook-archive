package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.sportsbook.wallet.domain.BalanceBucket;
import com.sportsbook.wallet.domain.LedgerEntry;
import com.sportsbook.wallet.domain.LedgerReason;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletTransferPlanTest {

  @Test
  void retainsNonNullTransferTopology() {
    var destination = new LedgerEntry.TransferLeg(UUID.randomUUID(), BalanceBucket.AVAILABLE);
    var source = new LedgerEntry.TransferLeg(UUID.randomUUID(), BalanceBucket.LOCKED);
    var plan = new WalletTransferPlan(destination, source, LedgerReason.BET_REFUND);

    assertThat(plan.destination()).isSameAs(destination);
    assertThat(plan.source()).isSameAs(source);
    assertThat(plan.reason()).isEqualTo(LedgerReason.BET_REFUND);
    assertThatNullPointerException()
        .isThrownBy(() -> new WalletTransferPlan(null, source, LedgerReason.BET_REFUND));
    assertThatNullPointerException()
        .isThrownBy(() -> new WalletTransferPlan(destination, null, LedgerReason.BET_REFUND));
    assertThatNullPointerException()
        .isThrownBy(() -> new WalletTransferPlan(destination, source, null));
  }
}
