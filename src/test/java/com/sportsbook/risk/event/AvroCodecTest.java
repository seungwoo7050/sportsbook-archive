package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.RequestedSelection;
import com.sportsbook.protocol.event.RiskLimitType;
import com.sportsbook.protocol.event.RiskLimitViolated;
import com.sportsbook.protocol.event.RiskPatternSuspected;
import com.sportsbook.protocol.event.RiskPatternType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.Test;

class AvroCodecTest {
  @Test
  void roundTripsOwnedInputAndSignalRecords() {
    BetPlacedRequested placed =
        new BetPlacedRequested(
            "bet",
            "user",
            BetSlipTypeTag.SINGLE,
            null,
            null,
            List.of(new RequestedSelection("event", "market", "selection", "2.00")),
            new Money(100L, "KRW"),
            "request",
            Instant.EPOCH);
    RiskLimitViolated limit =
        new RiskLimitViolated(
            "user", RiskLimitType.STAKE_DAILY, 90L, 100L, new Money(20L, "KRW"), Instant.EPOCH);
    RiskPatternSuspected pattern =
        new RiskPatternSuspected(
            "user", RiskPatternType.RAPID_BETTING, Map.of("action", "REVIEW"), Instant.EPOCH);

    assertRoundTrip(placed, BetPlacedRequested.class);
    assertRoundTrip(limit, RiskLimitViolated.class);
    assertRoundTrip(pattern, RiskPatternSuspected.class);
  }

  @Test
  void rejectsTrailingWireData() {
    RiskPatternSuspected record =
        new RiskPatternSuspected("user", RiskPatternType.RAPID_BETTING, Map.of(), Instant.EPOCH);
    byte[] encoded = AvroCodec.encode(record);
    byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);

    assertThatThrownBy(() -> AvroCodec.decode(trailing, RiskPatternSuspected.class))
        .isInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage("Avro payload contains trailing bytes");
  }

  private static <T extends SpecificRecordBase> void assertRoundTrip(T record, Class<T> type) {
    assertThat(AvroCodec.decode(AvroCodec.encode(record), type)).isEqualTo(record);
  }
}
