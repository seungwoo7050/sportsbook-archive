package com.sportsbook.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.error.WalletAccessDeniedException;
import com.sportsbook.wallet.service.command.CreditCommand;
import com.sportsbook.wallet.service.command.CreditReason;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletCreditAuthorizationTest {

  private static final UUID USER = UUID.fromString("019b76da-a000-7000-8000-000000000027");

  @Test
  void acceptsTheCallerSourceAndReasonAllowlist() {
    List<Attempt> allowed = allowed();

    assertThat(allowed).hasSize(5);
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
    List<Attempt> allowed = allowed();
    int rejected = 0;
    for (WalletCaller caller : WalletCaller.values()) {
      for (CreditCommand.Source source : CreditCommand.Source.values()) {
        for (CreditReason reason : CreditReason.values()) {
          Attempt attempt = new Attempt(caller, source, reason);
          if (!allowed.contains(attempt)) {
            Throwable error =
                catchThrowable(
                    () -> WalletTransferExecutor.requireAllowedCredit(caller, command(attempt)));
            assertThat(error)
                .isExactlyInstanceOf(WalletAccessDeniedException.class)
                .isNotInstanceOf(IllegalArgumentException.class);
            assertThat(((WalletAccessDeniedException) error).caller()).isEqualTo(caller);
            rejected++;
          }
        }
      }
    }
    assertThat(rejected).isEqualTo(25);
  }

  @Test
  void describesCreditCapabilityDenialsWithoutUsingArgumentErrors() {
    WalletAccessDeniedException denied =
        new WalletAccessDeniedException(WalletCaller.ADMIN, "credit source and reason");

    assertThat(denied.caller()).isEqualTo(WalletCaller.ADMIN);
    assertThat(denied.capability()).isEqualTo("credit source and reason");
    assertThat(denied).isNotInstanceOf(IllegalArgumentException.class);
    assertThatNullPointerException()
        .isThrownBy(() -> new WalletAccessDeniedException(null, "credit"));
    assertThatNullPointerException()
        .isThrownBy(() -> new WalletAccessDeniedException(WalletCaller.ADMIN, null));
  }

  private static List<Attempt> allowed() {
    return List.of(
        new Attempt(WalletCaller.BETTING, CreditCommand.Source.USER_LOCKED, CreditReason.REFUND),
        new Attempt(WalletCaller.SETTLEMENT, CreditCommand.Source.USER_LOCKED, CreditReason.VOID),
        new Attempt(WalletCaller.SETTLEMENT, CreditCommand.Source.USER_LOCKED, CreditReason.REFUND),
        new Attempt(WalletCaller.SETTLEMENT, CreditCommand.Source.HOUSE_POOL, CreditReason.PAYOUT),
        new Attempt(WalletCaller.ADMIN, CreditCommand.Source.HOUSE_POOL, CreditReason.REFUND));
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
