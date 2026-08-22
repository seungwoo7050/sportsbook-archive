package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DownstreamMalformedSuccessTest {

  @Test
  void classifiesAnUnreadableSuccessBodyAsAContractViolation() {
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl("http://downstream.test")
            .defaultHeader(DownstreamHeaders.SERVICE_NAME, DownstreamHeaders.ADMIN_API);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://downstream.test/malformed"))
        .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

    RestClient client = builder.build();
    assertThatThrownBy(
            () ->
                new DownstreamFailureMapper()
                    .execute(
                        () -> client.get().uri("/malformed").retrieve().body(ProbeResponse.class)))
        .isInstanceOf(DownstreamContractException.class)
        .hasMessageContaining("deserializable response body");
    server.verify();
  }

  private record ProbeResponse(String value) {}
}
