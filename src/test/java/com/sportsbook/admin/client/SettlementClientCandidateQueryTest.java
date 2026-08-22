package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SettlementClientCandidateQueryTest {

  @Test
  void fetchesCandidateWithOnlySettlementAuthentication() {
    UUID candidate = UUID.fromString("018f0000-0000-7000-8000-000000000156");
    RestClient.Builder builder = settlementBuilder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://settlement.test/internal/admin/result-candidates/" + candidate))
        .andExpect(method(GET))
        .andExpect(header(DownstreamHeaders.SERVICE_NAME, "admin-api"))
        .andExpect(header(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT))
        .andExpect(headerDoesNotExist(DownstreamHeaders.INTERNAL_SERVICE))
        .andExpect(headerDoesNotExist("Idempotency-Key"))
        .andRespond(withSuccess(response(candidate), MediaType.APPLICATION_JSON));

    SettlementCandidateView result = new SettlementClient(builder.build()).getCandidate(candidate);

    assertThat(result.candidateId()).isEqualTo(candidate);
    assertThat(result.state()).isEqualTo(SettlementCandidateView.State.ACCEPTED);
    assertThat(result.accepted()).isTrue();
    server.verify();
  }

  private static RestClient.Builder settlementBuilder() {
    return RestClient.builder()
        .baseUrl("http://settlement.test")
        .defaultHeader(DownstreamHeaders.SERVICE_NAME, DownstreamHeaders.ADMIN_API)
        .defaultHeader(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT);
  }

  private static String response(UUID candidate) {
    return """
        {"candidateId":"%s","eventId":"018f0000-0000-7000-8000-000000000157",
         "mode":"COMPLETED","settledAt":"2026-08-22T01:00:00Z",
         "receivedAt":"2026-08-22T01:00:01Z","state":"ACCEPTED",
         "replacesCandidateId":null,"decisionReason":"approved",
         "decidedAt":"2026-08-22T01:00:02Z","accepted":true}
        """
        .formatted(candidate);
  }
}
