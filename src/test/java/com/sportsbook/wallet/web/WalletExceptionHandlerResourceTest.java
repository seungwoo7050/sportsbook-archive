package com.sportsbook.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.wallet.domain.error.AccountNotFoundException;
import com.sportsbook.wallet.domain.error.WalletAdjustmentNotFoundException;
import com.sportsbook.wallet.domain.error.WalletOperationNotFoundException;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class WalletExceptionHandlerResourceTest {
  private final WalletExceptionHandler handler = new WalletExceptionHandler();

  @Test
  void mapsMissingResourcesWithoutReflectingTheirIdentifiers() {
    UUID userId = UUID.fromString("019b76da-a000-7000-8000-000000000311");
    UUID betId = UUID.fromString("019b76da-a000-7000-8000-000000000312");
    UUID revisionId = UUID.fromString("019b76da-a000-7000-8000-000000000313");
    AccountNotFoundException accountFailure = new AccountNotFoundException(userId);
    WalletOperationNotFoundException operationFailure = new WalletOperationNotFoundException(betId);
    WalletAdjustmentNotFoundException adjustmentFailure =
        new WalletAdjustmentNotFoundException(revisionId);

    ProblemDetail account = handler.accountNotFound(accountFailure, request("/wallet/account"));
    ProblemDetail operation = handler.operationNotFound(operationFailure, request("/wallet/debit"));
    ProblemDetail adjustment =
        handler.adjustmentNotFound(adjustmentFailure, request("/wallet/adjustment"));

    assertProblem(
        account,
        WalletError.ACCOUNT_NOT_FOUND,
        "The requested wallet account does not exist",
        "/wallet/account");
    assertProblem(
        operation,
        WalletError.OPERATION_NOT_FOUND,
        "The requested wallet operation does not exist",
        "/wallet/debit");
    assertProblem(
        adjustment,
        WalletError.ADJUSTMENT_NOT_FOUND,
        "The requested wallet adjustment does not exist",
        "/wallet/adjustment");
    assertThat(account.toString()).doesNotContain(userId.toString(), accountFailure.getMessage());
    assertThat(operation.toString())
        .doesNotContain(betId.toString(), operationFailure.getMessage());
    assertThat(adjustment.toString())
        .doesNotContain(revisionId.toString(), adjustmentFailure.getMessage());
  }

  private void assertProblem(
      ProblemDetail problem, WalletError error, String detail, String instance) {
    assertThat(problem.getStatus()).isEqualTo(error.httpStatus());
    assertThat(problem.getType()).isEqualTo(error.type());
    assertThat(problem.getTitle()).isEqualTo(error.title());
    assertThat(problem.getDetail()).isEqualTo(detail);
    assertThat(problem.getInstance()).isEqualTo(URI.create(instance));
    assertThat(problem.getProperties())
        .containsOnlyKeys("errorCode")
        .containsEntry("errorCode", error.errorCode());
  }

  private MockHttpServletRequest request(String path) {
    return new MockHttpServletRequest("GET", path);
  }
}
