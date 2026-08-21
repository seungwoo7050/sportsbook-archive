package com.sportsbook.wallet.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.WalletCreditReason;
import com.sportsbook.protocol.event.WalletCredited;
import com.sportsbook.protocol.event.WalletDebitFailed;
import com.sportsbook.protocol.event.WalletDebitFailureReason;
import com.sportsbook.protocol.event.WalletDebited;
import java.time.Instant;
import java.util.stream.Stream;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AvroSerializerTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-08-21T00:00:00.123Z");

  @ParameterizedTest
  @MethodSource("walletRecords")
  <T extends SpecificRecord> void roundTripsTheFrozenSharedSchema(T expected) {
    byte[] payload = AvroSerializer.serialize(expected);

    @SuppressWarnings("unchecked")
    Class<T> type = (Class<T>) expected.getClass();
    T decoded = AvroSerializer.deserialize(payload, type);

    assertThat(decoded).isEqualTo(expected);
    assertThat(decoded.getSchema().getFullName()).isEqualTo(expected.getSchema().getFullName());
  }

  private static Stream<SpecificRecord> walletRecords() {
    Money money = new Money(1_000L, "KRW");
    return Stream.of(
        new WalletDebited("user-1", money, "bet-1", "ledger-1", OCCURRED_AT),
        new WalletDebitFailed(
            "user-1", money, "bet-1", WalletDebitFailureReason.INSUFFICIENT_BALANCE, OCCURRED_AT),
        new WalletCredited(
            "user-1", money, "settlement-1", "ledger-2", WalletCreditReason.PAYOUT, OCCURRED_AT));
  }
}
