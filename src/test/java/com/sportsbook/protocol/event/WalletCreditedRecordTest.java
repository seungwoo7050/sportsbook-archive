package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WalletCreditedRecordTest {

  @Test
  void creditConfirmationRoundTrips() throws Exception {
    WalletCredited expected =
        WalletCredited.newBuilder()
            .setUserId("user-1")
            .setAmount(Money.newBuilder().setAmount(18_500).setCurrency("KRW").build())
            .setIdempotencyKey("bet-1:payout")
            .setLedgerTxId("ledger-2")
            .setReason(WalletCreditReason.PAYOUT)
            .setOccurredAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        WalletCredited.getClassSchema(),
        "userId",
        "amount",
        "idempotencyKey",
        "ledgerTxId",
        "reason",
        "occurredAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, WalletCredited.class)).isEqualTo(expected);
  }
}
