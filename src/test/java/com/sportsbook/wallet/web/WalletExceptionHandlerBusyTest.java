package com.sportsbook.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.wallet.domain.error.WalletBusyException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class WalletExceptionHandlerBusyTest {
  private final WalletExceptionHandler handler = new WalletExceptionHandler();

  @Test
  void directsRetryableDatabaseFailuresWithoutLeakingTheirContext() {
    IdempotencyKey key = IdempotencyKey.of("secret:busy-operation-key");
    RuntimeException cause = new RuntimeException("database diagnostics must stay private");
    WalletBusyException busy = new WalletBusyException(key, cause);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/v1/wallet/transactions/withdraw");

    ResponseEntity<ProblemDetail> response = handler.busy(busy, request);
    ProblemDetail problem = response.getBody();

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getHeaders().get(HttpHeaders.RETRY_AFTER)).containsExactly("1");
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(503);
    assertThat(problem.getType()).isEqualTo(URI.create("https://sportsbook/errors/wallet/busy"));
    assertThat(problem.getTitle()).isEqualTo("Wallet temporarily busy");
    assertThat(problem.getDetail()).isEqualTo("Retry the wallet request after one second");
    assertThat(problem.getInstance()).isEqualTo(URI.create(request.getRequestURI()));
    assertThat(problem.getProperties()).containsEntry("errorCode", "WALLET_BUSY");
    assertThat(problem.toString()).doesNotContain(key.value(), cause.getMessage());
  }
}
