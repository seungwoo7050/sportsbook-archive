package com.sportsbook.protocol.domain;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetSlipSettlementTest {

  @Test
  void wonPushAndVoidRequirePayoutSnapshots() {
    settled(SettlementResult.WON, Money.krw(18_500));
    settled(SettlementResult.PUSH, Money.krw(10_000));
    settled(SettlementResult.VOID, Money.krw(10_000));
    assertThatIllegalArgumentException().isThrownBy(() -> settled(SettlementResult.WON, null));
  }

  @Test
  void lostOutcomeMustNotCarryPayout() {
    settled(SettlementResult.LOST, null);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> settled(SettlementResult.LOST, Money.krw(0)));
  }

  @Test
  void settledSlipRequiresResultAndTimestamp() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> create(BetStatus.SETTLED, null, Instant.EPOCH, null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> create(BetStatus.SETTLED, SettlementResult.LOST, null, null));
  }

  @Test
  void activeSlipCannotCarrySettlementFields() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                create(BetStatus.PENDING, SettlementResult.WON, Instant.EPOCH, Money.krw(18_500)));
  }

  private BetSlip settled(SettlementResult result, Money payout) {
    return create(BetStatus.SETTLED, result, Instant.EPOCH, payout);
  }

  private BetSlip create(
      BetStatus status, SettlementResult result, Instant settledAt, Money payout) {
    BetSelection selection =
        new BetSelection(
            EventId.of(UUID.randomUUID()),
            MarketId.of(UUID.randomUUID()),
            MarketType.MATCH_RESULT_1X2,
            SelectionId.of(UUID.randomUUID()),
            Odds.ofDecimal("1.85"));
    return new BetSlip(
        BetId.of(UUID.randomUUID()),
        UserId.of(UUID.randomUUID()),
        new BetSlipType.Single(),
        List.of(selection),
        Money.krw(10_000),
        status,
        Instant.EPOCH,
        result,
        settledAt,
        payout);
  }
}
