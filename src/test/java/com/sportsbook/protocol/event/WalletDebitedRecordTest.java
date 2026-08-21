package com.sportsbook.protocol.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WalletDebitedRecordTest {

  @Test
  void debitConfirmationRoundTrips() throws Exception {
    WalletDebited expected =
        WalletDebited.newBuilder()
            .setUserId("user-1")
            .setAmount(Money.newBuilder().setAmount(10_000).setCurrency("KRW").build())
            .setIdempotencyKey("bet-1:debit")
            .setLedgerTxId("ledger-1")
            .setOccurredAt(Instant.parse("2026-08-21T00:00:00Z"))
            .build();
    AvroRecordTestSupport.assertFields(
        WalletDebited.getClassSchema(),
        "userId",
        "amount",
        "idempotencyKey",
        "ledgerTxId",
        "occurredAt");
    assertThat(AvroRecordTestSupport.roundTrip(expected, WalletDebited.class)).isEqualTo(expected);
  }
}
