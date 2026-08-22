package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.sportsbook.admin.client.DownstreamFailureMapper;
import com.sportsbook.admin.client.DownstreamStatusException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

class DownstreamRejectionResponseTest {

  @Test
  void relaysStatusContentTypeAndBodyWithoutCaching() {
    byte[] body =
        "{\"status\":409,\"code\":\"IDEMPOTENCY_CONFLICT\"}".getBytes(StandardCharsets.UTF_8);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    DownstreamStatusException failure =
        catchThrowableOfType(
            () ->
                new DownstreamFailureMapper()
                    .execute(
                        () -> {
                          throw HttpClientErrorException.create(
                              HttpStatus.CONFLICT,
                              "Conflict",
                              headers,
                              body,
                              StandardCharsets.UTF_8);
                        }),
            DownstreamStatusException.class);

    var response = new AdminExceptionHandler().relayDownstream(failure);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody()).containsExactly(body);
  }
}
