package com.sportsbook.wallet.web;

import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import java.util.Objects;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds stable RFC 9457 bodies without consulting mutable wallet state. */
public final class WalletProblems {
  public static final String ERROR_CODE = "errorCode";

  private WalletProblems() {}

  public static ProblemDetail from(WalletError error, String detail) {
    Objects.requireNonNull(error, "error");
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(error.httpStatus()), Objects.requireNonNull(detail, "detail"));
    problem.setType(error.type());
    problem.setTitle(error.title());
    problem.setProperty(ERROR_CODE, error.errorCode());
    return problem;
  }

  public static ProblemDetail from(WalletFailureSnapshot failure) {
    Objects.requireNonNull(failure, "failure");
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(failure.httpStatus()), failure.detail());
    problem.setType(errorFor(failure.code()).type());
    problem.setTitle(failure.title());
    problem.setProperty(ERROR_CODE, failure.code().wireCode());
    if (failure.balance() != null) {
      problem.setProperty("balance", failure.balance());
    }
    if (failure.expectedCurrency() != null) {
      problem.setProperty("expectedCurrency", failure.expectedCurrency());
    }
    return problem;
  }

  private static WalletError errorFor(WalletFailureCode code) {
    return switch (code) {
      case ACCOUNT_NOT_FOUND -> WalletError.ACCOUNT_NOT_FOUND;
      case CURRENCY_MISMATCH -> WalletError.CURRENCY_MISMATCH;
      case INSUFFICIENT_BALANCE -> WalletError.INSUFFICIENT_BALANCE;
      case ACCOUNT_SUSPENDED -> WalletError.ACCOUNT_RECOVERY_BLOCKED;
      case AMOUNT_OUT_OF_RANGE -> WalletError.AMOUNT_OUT_OF_RANGE;
    };
  }
}
