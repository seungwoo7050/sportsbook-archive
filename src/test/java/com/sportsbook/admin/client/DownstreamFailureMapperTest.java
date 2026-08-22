package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

class DownstreamFailureMapperTest {

  private final DownstreamFailureMapper mapper = new DownstreamFailureMapper();

  @Test
  void preservesAnExactDownstream4xxStatusContentTypeAndBody() {
    byte[] body = "{\"errorCode\":\"LIMIT_REJECTED\"}".getBytes(StandardCharsets.UTF_8);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    HttpClientErrorException rejection =
        HttpClientErrorException.create(
            HttpStatus.UNPROCESSABLE_ENTITY, "rejected", headers, body, StandardCharsets.UTF_8);

    DownstreamStatusException mapped =
        catchThrowableOfType(
            () ->
                mapper.execute(
                    (java.util.function.Supplier<Object>)
                        () -> {
                          throw rejection;
                        }),
            DownstreamStatusException.class);

    assertThat(mapped.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(mapped.contentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(mapped.body()).containsExactly(body);
    assertThat(mapped.getMessage()).doesNotContain("LIMIT_REJECTED");
    byte[] exposed = mapped.body();
    exposed[0] = 0;
    assertThat(mapped.body()).containsExactly(body);
  }
}
