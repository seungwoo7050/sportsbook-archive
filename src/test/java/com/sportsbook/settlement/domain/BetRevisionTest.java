package com.sportsbook.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetRevisionTest {

  @Test
  void incrementsOnlyCommittedSettledSnapshots() {
    Bet bet = pending();
    assertThatIllegalStateException()
        .isThrownBy(() -> bet.recordRevision(SettlementResult.LOST, Money.krw(0), Instant.EPOCH));
    bet.recordSettled(SettlementResult.WON, Money.krw(200), Instant.EPOCH);

    assertThat(
            bet.recordRevision(SettlementResult.LOST, Money.krw(0), Instant.EPOCH.plusSeconds(1)))
        .isOne();
    assertThat(
            bet.recordRevision(SettlementResult.PUSH, Money.krw(100), Instant.EPOCH.plusSeconds(2)))
        .isEqualTo(2);
    assertThat(bet.revisionNumber()).isEqualTo(2);
    assertThat(bet.result()).isEqualTo(SettlementResult.PUSH);
    assertThat(bet.payout()).isEqualTo(Money.krw(100));
  }

  private Bet pending() {
    return Bet.pending(
        UUID.randomUUID(),
        UUID.randomUUID(),
        SlipKind.SINGLE,
        null,
        null,
        EmbeddedMoney.of(Money.krw(100)),
        Instant.EPOCH,
        List.of(
            new BetSelection(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"))),
        Instant.EPOCH);
  }
}
