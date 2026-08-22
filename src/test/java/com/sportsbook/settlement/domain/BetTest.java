package com.sportsbook.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetTest {

  @Test
  void createsOrderedPendingSystemAggregate() {
    BetSelection first = selection();
    BetSelection second = selection();
    Bet bet = pending(SlipKind.SYSTEM, 1, 2, List.of(first, second));

    assertThat(bet.status()).isEqualTo(SettlementStatus.PENDING);
    assertThat(bet.slipType()).isEqualTo(new BetSlipType.System(1, 2));
    assertThat(bet.selections()).containsExactly(first, second);
    assertThat(first.legIndex()).isZero();
    assertThat(second.legIndex()).isOne();
    assertThatThrownBy(() -> bet.selections().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsInvalidSystemShapeAtCreation() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> pending(SlipKind.SYSTEM, 3, 2, List.of(selection(), selection())));
  }

  private static Bet pending(
      SlipKind kind, Integer minimumWins, Integer total, List<BetSelection> selections) {
    return Bet.pending(
        UUID.randomUUID(),
        UUID.randomUUID(),
        kind,
        minimumWins,
        total,
        new EmbeddedMoney(100, Currency.KRW),
        Instant.EPOCH,
        selections,
        Instant.EPOCH);
  }

  private static BetSelection selection() {
    return new BetSelection(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
  }
}
