package com.sportsbook.settlement.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

class WalletFailurePolicyTest {

  @Test
  void classifiesBusyProblemAsTransientWithoutLeakingDetail() {
    MockClientHttpResponse response =
        response(HttpStatus.SERVICE_UNAVAILABLE, "WALLET_BUSY", "database secret diagnostic");

    assertThatThrownBy(() -> WalletFailurePolicy.throwFor(response))
        .isInstanceOf(WalletFailurePolicy.TransientFailure.class)
        .satisfies(
            failure -> {
              WalletFailurePolicy.Failure walletFailure = (WalletFailurePolicy.Failure) failure;
              assertThat(walletFailure.errorCode()).isEqualTo("WALLET_BUSY");
              assertThat(walletFailure.getMessage()).doesNotContain("secret", "diagnostic");
            });
  }

  @Test
  void classifiesBusinessConflictAsPermanent() {
    MockClientHttpResponse response =
        response(HttpStatus.CONFLICT, "WALLET_IDEMPOTENCY_CONFLICT", "stored mismatch");

    assertThatThrownBy(() -> WalletFailurePolicy.throwFor(response))
        .isInstanceOf(WalletFailurePolicy.PermanentFailure.class)
        .hasMessageContaining("WALLET_IDEMPOTENCY_CONFLICT");
  }

  private static MockClientHttpResponse response(
      HttpStatus status, String errorCode, String detail) {
    String body =
        """
        {"type":"about:blank","title":"failure","status":%d,"detail":"%s",
         "instance":"/wallet","errorCode":"%s","extra":"allowed"}
        """
            .formatted(status.value(), detail, errorCode);
    return new MockClientHttpResponse(body.getBytes(UTF_8), status);
  }
}
