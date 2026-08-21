package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.RequestedSelection;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AcceptedBetExposureTest {
  @Test
  void singleAndMultipleKeepTheSubmittedAmount() {
    assertThat(AcceptedBetExposure.from(event(BetSlipTypeTag.SINGLE, null, null, 1, 75L)))
        .extracting(AcceptedBetExposure::totalAmount)
        .isEqualTo(75L);
    assertThat(AcceptedBetExposure.from(event(BetSlipTypeTag.MULTIPLE, null, null, 3, 90L)))
        .extracting(AcceptedBetExposure::totalAmount)
        .isEqualTo(90L);
  }

  @Test
  void systemMultipliesTheUnitAmountByExactCombinations() {
    assertThat(AcceptedBetExposure.from(event(BetSlipTypeTag.SYSTEM, 2, 5, 5, 100L)))
        .extracting(AcceptedBetExposure::totalAmount)
        .isEqualTo(1_000L);
    assertThat(AcceptedBetExposure.from(event(BetSlipTypeTag.SYSTEM, 3, 5, 5, 100L)))
        .extracting(AcceptedBetExposure::totalAmount)
        .isEqualTo(1_000L);
  }

  @Test
  void rejectsSlipShapeMismatches() {
    assertThatThrownBy(
            () -> AcceptedBetExposure.from(event(BetSlipTypeTag.SINGLE, null, null, 2, 1L)))
        .hasMessageContaining("exactly one");
    assertThatThrownBy(
            () -> AcceptedBetExposure.from(event(BetSlipTypeTag.MULTIPLE, null, null, 1, 1L)))
        .hasMessageContaining("at least two");
    assertThatThrownBy(() -> AcceptedBetExposure.from(event(BetSlipTypeTag.SYSTEM, 2, 4, 3, 1L)))
        .hasMessageContaining("totalSelections");
    assertThatThrownBy(() -> AcceptedBetExposure.from(event(BetSlipTypeTag.SYSTEM, 4, 3, 3, 1L)))
        .hasMessageContaining("minWins");
  }

  @Test
  void rejectsSystemFieldsOnUnitTotalSlips() {
    assertThatThrownBy(() -> AcceptedBetExposure.from(event(BetSlipTypeTag.MULTIPLE, 1, 2, 2, 1L)))
        .hasMessageContaining("non-SYSTEM");
  }

  @Test
  void rejectsUnsafeUnitAndCalculatedAmounts() {
    assertThatThrownBy(
            () -> AcceptedBetExposure.from(event(BetSlipTypeTag.SINGLE, null, null, 1, 0L)))
        .hasMessageContaining("positive");
    long unsafeUnit = SafeRedisNumber.MAX_VALUE / 3L + 1L;
    assertThatThrownBy(
            () -> AcceptedBetExposure.from(event(BetSlipTypeTag.SYSTEM, 2, 3, 3, unsafeUnit)))
        .hasMessageContaining("totalAmount");
  }

  @Test
  void capsEverySlipAtFifteenSelections() {
    assertThatThrownBy(
            () -> AcceptedBetExposure.from(event(BetSlipTypeTag.MULTIPLE, null, null, 16, 1L)))
        .hasMessageContaining("between 1 and 15");
  }

  private static BetPlacedRequested event(
      BetSlipTypeTag type, Integer minimum, Integer total, int selectionCount, long amount) {
    List<RequestedSelection> selections =
        IntStream.range(0, selectionCount).mapToObj(ignored -> new RequestedSelection()).toList();
    return BetPlacedRequested.newBuilder()
        .setBetId("10000000-0000-4000-8000-000000000001")
        .setUserId("20000000-0000-4000-8000-000000000001")
        .setSlipType(type)
        .setSystemMinWins(minimum)
        .setSystemTotalSelections(total)
        .setSelections(selections)
        .setStake(new Money(amount, "KRW"))
        .setIdempotencyKey("accepted-exposure-test")
        .setRequestedAt(java.time.Instant.EPOCH)
        .build();
  }
}
