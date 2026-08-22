package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WalletProblemDetailTest {

  @Test
  void readsErrorCodeFromCompleteExtensibleProblemBody() throws Exception {
    String body =
        """
        {
          "type":"https://sportsbook.test/problems/wallet-busy",
          "title":"Wallet busy",
          "status":503,
          "detail":"Retry later",
          "instance":"/internal/v1/wallet/transactions/credit",
          "errorCode":"WALLET_BUSY",
          "balance":{"amount":10,"currency":"KRW"},
          "futureField":"allowed"
        }
        """;

    WalletProblemDetail problem = new ObjectMapper().readValue(body, WalletProblemDetail.class);

    assertThat(problem.status()).isEqualTo(503);
    assertThat(problem.errorCode()).isEqualTo("WALLET_BUSY");
    assertThat(problem.detail()).isEqualTo("Retry later");
  }
}
