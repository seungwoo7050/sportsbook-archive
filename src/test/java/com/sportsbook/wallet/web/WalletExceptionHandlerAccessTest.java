package com.sportsbook.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.domain.error.CurrencyMismatchException;
import com.sportsbook.wallet.domain.error.WalletAccessDeniedException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class WalletExceptionHandlerAccessTest {
  private final WalletExceptionHandler handler = new WalletExceptionHandler();

  @Test
  void mapsCurrencyMismatchWithoutReflectingTheActualCurrency() {
    CurrencyMismatchException failure = new CurrencyMismatchException(Currency.KRW, Currency.USD);

    ProblemDetail problem = handler.currencyMismatch(failure, request("/wallet/account"));

    assertProblem(
        problem,
        WalletError.CURRENCY_MISMATCH,
        "The requested currency does not match the wallet account",
        "/wallet/account");
    assertThat(problem.getProperties())
        .containsOnlyKeys("errorCode", "expectedCurrency")
        .containsEntry("expectedCurrency", Currency.KRW);
    assertThat(problem.toString()).doesNotContain(Currency.USD.name(), failure.getMessage());
  }

  @Test
  void mapsSemanticAccessDenialsWithoutReflectingCapabilities() {
    WalletAccessDeniedException failure =
        new WalletAccessDeniedException(WalletCaller.ADMIN, "secret credit source and reason");

    ProblemDetail problem = handler.accessDenied(failure, request("/wallet/credit"));

    assertProblem(
        problem,
        WalletError.ACCESS_DENIED,
        "Authenticated caller cannot perform this wallet operation",
        "/wallet/credit");
    assertThat(problem.getProperties()).containsOnlyKeys("errorCode");
    assertThat(problem.toString())
        .doesNotContain(WalletCaller.ADMIN.wireName(), failure.capability(), failure.getMessage());
  }

  private void assertProblem(
      ProblemDetail problem, WalletError error, String detail, String instance) {
    assertThat(problem.getStatus()).isEqualTo(error.httpStatus());
    assertThat(problem.getType()).isEqualTo(error.type());
    assertThat(problem.getTitle()).isEqualTo(error.title());
    assertThat(problem.getDetail()).isEqualTo(detail);
    assertThat(problem.getInstance()).isEqualTo(URI.create(instance));
    assertThat(problem.getProperties()).containsEntry("errorCode", error.errorCode());
  }

  private MockHttpServletRequest request(String path) {
    return new MockHttpServletRequest("GET", path);
  }
}
