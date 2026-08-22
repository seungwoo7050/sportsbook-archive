package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class WalletAuthenticationHeadersTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef";

  @Test
  void replacesCallerSuppliedValuesWithOneExactCredentialPair() {
    WalletAuthenticationHeaders writer =
        new WalletAuthenticationHeaders(new WalletCredentials(SECRET));
    HttpHeaders headers = new HttpHeaders();
    headers.add(WalletAuthenticationHeaders.SERVICE_HEADER, "attacker");
    headers.add(WalletAuthenticationHeaders.API_KEY_HEADER, "attacker-secret");

    writer.apply(headers);

    assertThat(headers.get(WalletAuthenticationHeaders.SERVICE_HEADER))
        .containsExactly("settlement-service");
    assertThat(headers.get(WalletAuthenticationHeaders.API_KEY_HEADER)).containsExactly(SECRET);
  }
}
