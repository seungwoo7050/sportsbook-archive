package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossClientSecretIsolationTest {

  @Test
  void isolatesAllFourCredentialsInOneApplicationContext() throws Exception {
    try (CrossClientIntegrationFixture fixture = new CrossClientIntegrationFixture()) {
      fixture.invoke("walletRestClient", "/wallet");
      fixture.invoke("riskRestClient", "/risk");
      fixture.invoke("oddsRestClient", "/odds");
      fixture.invoke("settlementRestClient", "/settlement");

      assertInternal(fixture.headers("/wallet"), ClientIsolationFixture.WALLET);
      assertInternal(fixture.headers("/risk"), ClientIsolationFixture.RISK);
      assertInternal(fixture.headers("/odds"), ClientIsolationFixture.ODDS);
      Map<String, List<String>> settlement = fixture.headers("/settlement");
      assertThat(settlement.get(lower(DownstreamHeaders.SERVICE_NAME)))
          .containsExactly(DownstreamHeaders.ADMIN_API);
      assertThat(settlement.get(lower(DownstreamHeaders.API_KEY)))
          .containsExactly(ClientIsolationFixture.SETTLEMENT);
      assertThat(settlement)
          .doesNotContainKeys(
              lower(DownstreamHeaders.INTERNAL_SERVICE), lower(DownstreamHeaders.INTERNAL_API_KEY));
      assertOnlyExpectedSecret(settlement, ClientIsolationFixture.SETTLEMENT);
    }
  }

  private static void assertInternal(Map<String, List<String>> headers, String expectedSecret) {
    assertThat(headers.get(lower(DownstreamHeaders.INTERNAL_SERVICE)))
        .containsExactly(DownstreamHeaders.ADMIN_API);
    assertThat(headers.get(lower(DownstreamHeaders.INTERNAL_API_KEY)))
        .containsExactly(expectedSecret);
    assertThat(headers)
        .doesNotContainKeys(
            lower(DownstreamHeaders.SERVICE_NAME), lower(DownstreamHeaders.API_KEY));
    assertOnlyExpectedSecret(headers, expectedSecret);
  }

  private static void assertOnlyExpectedSecret(
      Map<String, List<String>> headers, String expectedSecret) {
    String[] unexpected =
        java.util.stream.Stream.of(
                ClientIsolationFixture.WALLET,
                ClientIsolationFixture.RISK,
                ClientIsolationFixture.ODDS,
                ClientIsolationFixture.SETTLEMENT)
            .filter(secret -> !secret.equals(expectedSecret))
            .toArray(String[]::new);
    assertThat(headers.values())
        .allSatisfy(values -> assertThat(values).doesNotContain(unexpected));
  }

  private static String lower(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
