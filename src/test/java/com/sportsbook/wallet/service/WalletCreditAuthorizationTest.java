package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletCreditAuthorizationTest {

  private static final UUID USER = UUID.fromString("019b76da-a000-7000-8000-000000000027");

  @Test
  void acceptsTheCallerSourceAndReasonAllowlist() {
    List<Attempt> allowed =
        List.of(
            new Attempt(
                WalletCaller.BETTING, CreditCommand.Source.USER_LOCKED, CreditReason.REFUND),
            new Attempt(
                WalletCaller.SETTLEMENT, CreditCommand.Source.USER_LOCKED, CreditReason.VOID),
            new Attempt(
                WalletCaller.SETTLEMENT, CreditCommand.Source.USER_LOCKED, CreditReason.REFUND),
            new Attempt(
                WalletCaller.SETTLEMENT, CreditCommand.Source.HOUSE_POOL, CreditReason.PAYOUT),
            new Attempt(WalletCaller.ADMIN, CreditCommand.Source.HOUSE_POOL, CreditReason.REFUND));

    allowed.forEach(
        attempt ->
            assertThatCode(
                    () ->
                        WalletTransferExecutor.requireAllowedCredit(
                            attempt.caller(), command(attempt)))
                .doesNotThrowAnyException());
  }

  @Test
  void rejectsEveryCallerSourceAndReasonOutsideTheAllowlist() {
    List<Attempt> forbidden =
        List.of(
            new Attempt(
                WalletCaller.PLATFORM, CreditCommand.Source.HOUSE_POOL, CreditReason.PAYOUT),
            new Attempt(WalletCaller.GATEWAY, CreditCommand.Source.HOUSE_POOL, CreditReason.REFUND),
            new Attempt(WalletCaller.BETTING, CreditCommand.Source.USER_LOCKED, CreditReason.VOID),
            new Attempt(WalletCaller.BETTING, CreditCommand.Source.HOUSE_POOL, CreditReason.REFUND),
            new Attempt(
                WalletCaller.SETTLEMENT, CreditCommand.Source.HOUSE_POOL, CreditReason.REFUND),
            new Attempt(
                WalletCaller.SETTLEMENT, CreditCommand.Source.USER_LOCKED, CreditReason.PAYOUT),
            new Attempt(WalletCaller.ADMIN, CreditCommand.Source.HOUSE_POOL, CreditReason.PAYOUT),
            new Attempt(WalletCaller.ADMIN, CreditCommand.Source.USER_LOCKED, CreditReason.REFUND));

    forbidden.forEach(
        attempt ->
            assertThatThrownBy(
                    () ->
                        WalletTransferExecutor.requireAllowedCredit(
                            attempt.caller(), command(attempt)))
                .isInstanceOf(RuntimeException.class));
  }

  private static CreditCommand command(Attempt attempt) {
    String key =
        "credit:"
            + attempt.caller().name().toLowerCase()
            + ":"
            + attempt.source().name().toLowerCase()
            + ":"
            + attempt.reason().name().toLowerCase();
    return new CreditCommand(
        USER, Money.krw(1L), attempt.source(), attempt.reason(), IdempotencyKey.of(key));
  }

  private record Attempt(WalletCaller caller, CreditCommand.Source source, CreditReason reason) {}
}
