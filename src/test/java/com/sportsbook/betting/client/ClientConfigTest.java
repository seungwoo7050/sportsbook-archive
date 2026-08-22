package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.RestClient;

class ClientConfigTest {

  @Test
  void isolatesRiskAndWalletCredentials() {
    AtomicReference<MockClientHttpRequest> riskRequest = new AtomicReference<>();
    AtomicReference<MockClientHttpRequest> walletRequest = new AtomicReference<>();
    RestClient risk =
        ClientConfig.client(
            RestClient.builder(), "http://risk", "r".repeat(32), factory(riskRequest));
    RestClient wallet =
        ClientConfig.client(
            RestClient.builder(), "http://wallet", "w".repeat(32), factory(walletRequest));

    risk.get().uri("/probe").retrieve().toBodilessEntity();
    wallet.get().uri("/probe").retrieve().toBodilessEntity();

    assertThat(riskRequest.get().getHeaders().get("X-Internal-Service"))
        .containsExactly("betting-service");
    assertThat(riskRequest.get().getHeaders().get("X-Internal-Api-Key"))
        .containsExactly("r".repeat(32));
    assertThat(walletRequest.get().getHeaders().get("X-Internal-Api-Key"))
        .containsExactly("w".repeat(32));
  }

  private ClientHttpRequestFactory factory(AtomicReference<MockClientHttpRequest> captured) {
    return (uri, method) -> {
      MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
      request.setResponse(new MockClientHttpResponse(new byte[0], HttpStatus.OK));
      captured.set(request);
      return request;
    };
  }
}
