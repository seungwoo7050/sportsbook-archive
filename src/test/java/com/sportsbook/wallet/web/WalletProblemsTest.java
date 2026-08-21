package com.sportsbook.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ProblemDetail;

class WalletProblemsTest {

  @Test
  void shapesGenericWalletProblems() {
    ProblemDetail problem =
        WalletProblems.from(WalletError.ACCESS_DENIED, "Caller cannot use route");

    assertThat(problem.getStatus()).isEqualTo(403);
    assertThat(problem.getType()).isEqualTo(WalletError.ACCESS_DENIED.type());
    assertThat(problem.getTitle()).isEqualTo("Wallet access denied");
    assertThat(problem.getDetail()).isEqualTo("Caller cannot use route");
    assertThat(problem.getInstance()).isNull();
    assertThat(problem.getProperties())
        .containsExactly(Map.entry(WalletProblems.ERROR_CODE, "WALLET_ACCESS_DENIED"));
  }

  @ParameterizedTest
  @MethodSource("failureTypes")
  void mapsEveryDurableFailureType(WalletFailureCode code, WalletError error) {
    WalletFailureSnapshot failure = WalletFailureSnapshot.of(code, "stored detail");

    ProblemDetail problem = WalletProblems.from(failure);

    assertThat(problem.getStatus()).isEqualTo(code.httpStatus());
    assertThat(problem.getType()).isEqualTo(error.type());
    assertThat(problem.getTitle()).isEqualTo(code.title());
    assertThat(problem.getDetail()).isEqualTo("stored detail");
    assertThat(problem.getProperties())
        .containsExactly(Map.entry(WalletProblems.ERROR_CODE, code.wireCode()));
  }

  @Test
  void addsOnlyPersistedBalanceFacts() {
    Money balance = Money.krw(700L);
    ProblemDetail problem =
        WalletProblems.from(
            WalletFailureSnapshot.withBalance(
                WalletFailureCode.INSUFFICIENT_BALANCE, "stored balance", balance));

    assertThat(problem.getProperties())
        .containsEntry(WalletProblems.ERROR_CODE, "WALLET_INSUFFICIENT_BALANCE")
        .containsEntry("balance", balance)
        .doesNotContainKey("expectedCurrency");
  }

  @Test
  void addsOnlyPersistedExpectedCurrencyFacts() {
    ProblemDetail problem =
        WalletProblems.from(
            WalletFailureSnapshot.currencyMismatch("stored currency", Currency.USD));

    assertThat(problem.getProperties())
        .containsEntry(WalletProblems.ERROR_CODE, "WALLET_CURRENCY_MISMATCH")
        .containsEntry("expectedCurrency", Currency.USD)
        .doesNotContainKey("balance");
  }

  @Test
  void rejectsMissingProblemInputs() {
    assertThatNullPointerException()
        .isThrownBy(() -> WalletProblems.from((WalletError) null, "detail"));
    assertThatNullPointerException()
        .isThrownBy(() -> WalletProblems.from(WalletError.INVALID_REQUEST, null));
    assertThatNullPointerException()
        .isThrownBy(() -> WalletProblems.from((WalletFailureSnapshot) null));
  }

  private static Stream<Arguments> failureTypes() {
    return Stream.of(
        arguments(WalletFailureCode.ACCOUNT_NOT_FOUND, WalletError.ACCOUNT_NOT_FOUND),
        arguments(WalletFailureCode.CURRENCY_MISMATCH, WalletError.CURRENCY_MISMATCH),
        arguments(WalletFailureCode.INSUFFICIENT_BALANCE, WalletError.INSUFFICIENT_BALANCE),
        arguments(WalletFailureCode.ACCOUNT_SUSPENDED, WalletError.ACCOUNT_RECOVERY_BLOCKED),
        arguments(WalletFailureCode.AMOUNT_OUT_OF_RANGE, WalletError.AMOUNT_OUT_OF_RANGE));
  }
}
