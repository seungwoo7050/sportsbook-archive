package com.sportsbook.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetTerminalOutcomeTest {

  @Test
  void recordsResultVoidAsSettledRatherThanLifecycleVoided() {
    Bet bet = pending();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    bet.recordSettled(SettlementResult.VOID, Money.krw(100), now);

    assertThat(bet.status()).isEqualTo(SettlementStatus.SETTLED);
    assertThat(bet.result()).isEqualTo(SettlementResult.VOID);
    assertThat(bet.payout()).isEqualTo(Money.krw(100));
    assertThat(bet.settledAt()).isEqualTo(now);
  }

  @Test
  void reservesVoidedStateForWholeSlipVoid() {
    Bet bet = pending();

    bet.recordVoided(Money.krw(100), Instant.EPOCH);

    assertThat(bet.status()).isEqualTo(SettlementStatus.VOIDED);
    assertThatIllegalStateException()
        .isThrownBy(() -> bet.recordSettled(SettlementResult.WON, Money.krw(200), Instant.EPOCH));
  }

  @Test
  void rejectsNegativeOrCrossCurrencyPayouts() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> pending().recordSettled(SettlementResult.WON, Money.krw(-1), Instant.EPOCH));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> pending().recordSettled(SettlementResult.WON, Money.usd(1), Instant.EPOCH));
  }

  private static Bet pending() {
    BetSelection leg =
        new BetSelection(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2.0000"));
    return Bet.pending(
        UUID.randomUUID(),
        UUID.randomUUID(),
        SlipKind.SINGLE,
        null,
        null,
        new EmbeddedMoney(100, Currency.KRW),
        Instant.EPOCH,
        List.of(leg),
        Instant.EPOCH);
  }
}
