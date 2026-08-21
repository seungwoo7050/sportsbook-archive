package com.sportsbook.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletFailureCode;
import com.sportsbook.wallet.domain.WalletFailureSnapshot;
import com.sportsbook.wallet.domain.error.IdempotencyConflictException;
import com.sportsbook.wallet.domain.error.WalletRejectedException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class WalletExceptionHandlerReplayTest {
  private final WalletExceptionHandler handler = new WalletExceptionHandler();

  @Test
  void replaysOnlyPersistedRejectionFacts() {
    Money storedBalance = Money.krw(700L);
    WalletFailureSnapshot stored =
        WalletFailureSnapshot.withBalance(
            WalletFailureCode.INSUFFICIENT_BALANCE, "stored balance at rejection", storedBalance);
    MockHttpServletRequest request = request("/internal/v1/wallet/transactions/debit");
    request.setQueryString("apiKey=must-not-become-instance");

    ProblemDetail first = handler.rejected(new WalletRejectedException(stored), request);
    ProblemDetail replay = handler.rejected(new WalletRejectedException(stored), request);

    assertThat(first.getStatus()).isEqualTo(422);
    assertThat(first.getTitle()).isEqualTo("Insufficient balance");
    assertThat(first.getDetail()).isEqualTo("stored balance at rejection");
    assertThat(first.getType())
        .isEqualTo(URI.create("https://sportsbook/errors/wallet/insufficient-balance"));
    assertThat(first.getInstance()).isEqualTo(URI.create(request.getRequestURI()));
    assertThat(first.getProperties())
        .containsEntry("errorCode", "WALLET_INSUFFICIENT_BALANCE")
        .containsEntry("balance", storedBalance)
        .doesNotContainKey("expectedCurrency");
    assertThat(replay.getProperties()).isEqualTo(first.getProperties());
    assertThat(replay.getDetail()).isEqualTo(first.getDetail());
  }

  @Test
  void preservesPersistedCurrencyMismatchFacts() {
    WalletFailureSnapshot stored =
        WalletFailureSnapshot.currencyMismatch("stored currency mismatch", Currency.KRW);

    ProblemDetail replay =
        handler.rejected(
            new WalletRejectedException(stored),
            request("/internal/v1/wallet/transactions/credit"));

    assertThat(replay.getStatus()).isEqualTo(422);
    assertThat(replay.getProperties())
        .containsEntry("errorCode", "WALLET_CURRENCY_MISMATCH")
        .containsEntry("expectedCurrency", Currency.KRW)
        .doesNotContainKey("balance");
  }

  @Test
  void reportsConflictsWithoutReflectingTheirIdentity() {
    IdempotencyKey key = IdempotencyKey.of("secret:conflicting-operation-key");
    IdempotencyConflictException conflict = new IdempotencyConflictException(key);

    ProblemDetail problem =
        handler.idempotencyConflict(conflict, request("/internal/v1/wallet/transactions/deposit"));

    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getTitle()).isEqualTo("Idempotency key conflict");
    assertThat(problem.getDetail())
        .isEqualTo("Idempotency key belongs to a different wallet request");
    assertThat(problem.getProperties()).containsEntry("errorCode", "WALLET_IDEMPOTENCY_CONFLICT");
    assertThat(problem.toString()).doesNotContain(key.value(), conflict.getMessage());
  }

  private MockHttpServletRequest request(String path) {
    return new MockHttpServletRequest("POST", path);
  }
}
