package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WalletCreditPurposeTest {

  @Test
  void exposesOnlyWalletAuthorizedSettlementMeanings() {
    assertThat(
            Map.of(
                WalletCreditPurpose.WHOLE_SLIP_VOID,
                "USER_LOCKED:VOID",
                WalletCreditPurpose.RETURNED_STAKE,
                "USER_LOCKED:REFUND",
                WalletCreditPurpose.PROFIT_PAYOUT,
                "HOUSE_POOL:PAYOUT"))
        .allSatisfy(
            (purpose, wire) ->
                assertThat(purpose.source() + ":" + purpose.reason()).isEqualTo(wire));
  }
}
