package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class InternalClientHeadersTest {

  @Test
  void overwritesCallerHeadersWithOwnedCredential() throws IOException {
    MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("/"));
    request.getHeaders().add(InternalClientHeaders.SERVICE_HEADER, "spoofed");

    new InternalClientHeaders("s".repeat(32))
        .intercept(
            request,
            new byte[0],
            (outbound, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

    assertThat(request.getHeaders().get(InternalClientHeaders.SERVICE_HEADER))
        .containsExactly("betting-service");
    assertThat(request.getHeaders().get(InternalClientHeaders.KEY_HEADER))
        .containsExactly("s".repeat(32));
  }
}
