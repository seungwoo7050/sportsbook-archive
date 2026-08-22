package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RiskClientGetTest {

  @Test
  void fetchesTheExactUserAndValidatesAllSevenEntries() {
    UUID userId = UUID.fromString("018f0000-0000-7000-8000-000000000123");
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl("http://risk.test")
            .defaultHeader(DownstreamHeaders.INTERNAL_SERVICE, DownstreamHeaders.ADMIN_API)
            .defaultHeader(DownstreamHeaders.INTERNAL_API_KEY, ClientIsolationFixture.RISK);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://risk.test/internal/v1/risk/limits/" + userId))
        .andExpect(method(GET))
        .andExpect(header(DownstreamHeaders.INTERNAL_SERVICE, "admin-api"))
        .andExpect(header(DownstreamHeaders.INTERNAL_API_KEY, ClientIsolationFixture.RISK))
        .andRespond(withSuccess(response(userId), MediaType.APPLICATION_JSON));

    RiskLimitsResponse result = new RiskClient(builder.build()).getLimits(userId);

    assertThat(result.userId()).isEqualTo(userId);
    assertThat(result.limits()).hasSize(7);
    assertThat(result.limits().get(6).type()).isEqualTo(RiskLimitType.SELECTIONS_PER_MINUTE);
    assertThat(result.limits().get(6).currency()).isNull();
    server.verify();
  }

  private static String response(UUID userId) {
    return """
        {"userId":"%s","limits":[
          {"type":"STAKE_DAILY","currency":"KRW","value":1000,"source":"POLICY"},
          {"type":"STAKE_DAILY","currency":"USD","value":100,"source":"POLICY"},
          {"type":"STAKE_WEEKLY","currency":"KRW","value":5000,"source":"POLICY"},
          {"type":"STAKE_WEEKLY","currency":"USD","value":500,"source":"POLICY"},
          {"type":"STAKE_MONTHLY","currency":"KRW","value":20000,"source":"POLICY"},
          {"type":"STAKE_MONTHLY","currency":"USD","value":2000,"source":"POLICY"},
          {"type":"SELECTIONS_PER_MINUTE","value":20,"source":"OVERRIDE"}
        ]}
        """
        .formatted(userId);
  }
}
