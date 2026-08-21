package com.sportsbook.gateway.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gateway.ratelimit.enabled=false",
      "gateway.downstream.wallet.api-key=fixture-wallet-key-32-characters-long"
    })
class WebSocketEndpointTest {

  @LocalServerPort private int port;
  @MockBean JwtDecoder jwtDecoder;

  @Test
  void exposesDeclaredEndpointsForAllowedBrowserOrigins() throws Exception {
    WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
    headers.setOrigin("http://localhost:3000");

    for (String path : new String[] {"/ws/v1/odds", "/ws/v1/bets"}) {
      WebSocketSession session =
          new StandardWebSocketClient()
              .execute(new TextWebSocketHandler() {}, headers, endpoint(path))
              .get(3, TimeUnit.SECONDS);
      assertThat(session.isOpen()).isTrue();
      session.close();
    }
  }

  @Test
  void rejectsUntrustedOriginsAndUndeclaredEndpoints() {
    WebSocketHttpHeaders untrusted = new WebSocketHttpHeaders();
    untrusted.setOrigin("https://untrusted.example");

    assertThatThrownBy(
            () ->
                new StandardWebSocketClient()
                    .execute(new TextWebSocketHandler() {}, untrusted, endpoint("/ws/v1/odds"))
                    .get(3, TimeUnit.SECONDS))
        .rootCause()
        .hasMessageContaining("[403]");
    assertThatThrownBy(
            () ->
                new StandardWebSocketClient()
                    .execute(
                        new TextWebSocketHandler() {}, endpoint("/ws/v1/undeclared").toString())
                    .get(3, TimeUnit.SECONDS))
        .rootCause()
        .hasMessageContaining("[401]");
  }

  private URI endpoint(String path) {
    return URI.create("ws://localhost:" + port + path);
  }
}
