package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpRequest;

final class ClientIsolationFixture {

  static final String WALLET = "wallet-admin-test-key-000000000001";
  static final String RISK = "risk-admin-test-key-00000000000002";
  static final String ODDS = "odds-admin-test-key-00000000000003";
  static final String SETTLEMENT = "settlement-admin-test-key-000000004";

  private ClientIsolationFixture() {}

  static DownstreamProperties properties() {
    return new DownstreamProperties(
        URI.create("https://wallet.test"),
        URI.create("https://risk.test"),
        URI.create("https://odds.test"),
        URI.create("https://settlement.test"),
        Duration.ofMillis(200),
        Duration.ofSeconds(2));
  }

  static DownstreamCredentials credentials() {
    return new DownstreamCredentials(WALLET, RISK, ODDS, SETTLEMENT);
  }

  static void assertStandardCredential(HttpRequest request, String expected) {
    assertThat(request.getHeaders().get(DownstreamHeaders.INTERNAL_SERVICE))
        .containsExactly(DownstreamHeaders.ADMIN_API);
    assertThat(request.getHeaders().get(DownstreamHeaders.INTERNAL_API_KEY))
        .containsExactly(expected);
    assertThat(request.getHeaders())
        .doesNotContainKeys(DownstreamHeaders.SERVICE_NAME, DownstreamHeaders.API_KEY);
    assertThat(request.getHeaders().values())
        .allSatisfy(
            values ->
                assertThat(values)
                    .doesNotContain(
                        expected.equals(WALLET) ? RISK : WALLET,
                        expected.equals(ODDS) ? RISK : ODDS,
                        SETTLEMENT));
  }
}
