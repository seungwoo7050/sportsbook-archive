package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ClientPropertiesTest {

  @Test
  void suppliesTimeoutsAndKeepsCallerSecretsDistinct() {
    ClientProperties properties =
        new ClientProperties(
            "http://risk", "http://wallet", null, null, "r".repeat(32), "w".repeat(32));

    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(200));
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofMillis(500));
    assertThat(properties.toString())
        .contains("riskApiKey=<redacted>", "walletApiKey=<redacted>")
        .doesNotContain(properties.riskApiKey(), properties.walletApiKey());
  }

  @Test
  void failsFastForShortOrSharedSecrets() {
    assertThatThrownBy(
            () ->
                new ClientProperties(
                    "http://risk", "http://wallet", null, null, "short", "w".repeat(32)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new ClientProperties(
                    "http://risk", "http://wallet", null, null, "x".repeat(32), "x".repeat(32)))
        .hasMessageContaining("distinct");
  }

  @Test
  void rejectsUnsafeOrSharedDependencyDestinations() {
    for (String endpoint :
        java.util.List.of(
            "risk.internal",
            "ftp://risk.internal",
            "http://user@risk.internal",
            "http://risk.internal/v1",
            "http://risk.internal?probe=true",
            "http://risk.internal#probe")) {
      assertThatThrownBy(
              () ->
                  new ClientProperties(
                      endpoint,
                      "http://wallet.internal",
                      null,
                      null,
                      "r".repeat(32),
                      "w".repeat(32)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("HTTP(S) origin");
    }
    assertThatThrownBy(
            () ->
                new ClientProperties(
                    "https://shared.internal/",
                    "https://shared.internal/",
                    null,
                    null,
                    "r".repeat(32),
                    "w".repeat(32)))
        .hasMessageContaining("destinations must be distinct");
    for (String[] shared :
        new String[][] {
          {"http://SHARED.internal", "http://shared.internal:80/"},
          {"https://shared.internal/", "https://SHARED.internal:443"}
        }) {
      assertThatThrownBy(
              () ->
                  new ClientProperties(
                      shared[0], shared[1], null, null, "r".repeat(32), "w".repeat(32)))
          .hasMessageContaining("destinations must be distinct");
    }
  }
}
