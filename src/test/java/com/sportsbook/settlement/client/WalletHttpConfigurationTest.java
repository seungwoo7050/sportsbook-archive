package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

class WalletHttpConfigurationTest {

  @Test
  void appliesBoundedConnectAndReadTimeoutsToTheWireTransport() {
    WalletHttpProperties properties =
        new WalletHttpProperties(Duration.ofSeconds(2), Duration.ofSeconds(4));
    JdkClientHttpRequestFactory factory =
        (JdkClientHttpRequestFactory)
            new WalletHttpConfiguration().settlementWalletRequestFactory(properties);

    HttpClient client = (HttpClient) ReflectionTestUtils.getField(factory, "httpClient");
    Duration readTimeout = (Duration) ReflectionTestUtils.getField(factory, "readTimeout");

    assertThat(client.connectTimeout()).contains(Duration.ofSeconds(2));
    assertThat(readTimeout).isEqualTo(Duration.ofSeconds(4));
  }
}
