package com.sportsbook.gateway.routing;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gateway.ratelimit.enabled=false",
      "gateway.downstream.wallet.api-key=fixture-wallet-key-32-characters-long"
    })
class ClosedPortProxyTest {

  private static final ServerSocket RESERVED_PORT = reservedPort();

  @Autowired TestRestTemplate http;
  @MockBean JwtDecoder jwtDecoder;

  @DynamicPropertySource
  static void downstream(DynamicPropertyRegistry registry) {
    registry.add(
        "gateway.downstream.odds-feed-uri",
        () -> "http://127.0.0.1:" + RESERVED_PORT.getLocalPort());
  }

  @Test
  void mapsAnonymousConnectionRefusalWithoutReauthentication() throws IOException {
    RESERVED_PORT.close();
    ResponseEntity<String> response = http.getForEntity("/api/v1/events/unavailable", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(read(response.getBody(), "$.status").toString()).isEqualTo("502");
    assertThat(read(response.getBody(), "$.errorCode").toString()).isEqualTo("GATEWAY_BAD_GATEWAY");
    assertThat(read(response.getBody(), "$.instance").toString())
        .isEqualTo("/api/v1/events/unavailable");
  }

  private static ServerSocket reservedPort() {
    try {
      return new ServerSocket(0);
    } catch (IOException failure) {
      throw new ExceptionInInitializerError(failure);
    }
  }
}
