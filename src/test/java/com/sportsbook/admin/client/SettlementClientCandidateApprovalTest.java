package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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

class SettlementClientCandidateApprovalTest {

  @Test
  void postsAnEmptyApprovalWithTheExactSettlementHeaders() {
    UUID candidate = UUID.fromString("018f0000-0000-7000-8000-000000000163");
    UUID key = UUID.fromString("018f0000-0000-7000-8000-000000000164");
    RestClient.Builder builder = settlementBuilder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(
            requestTo(
                "http://settlement.test/internal/admin/result-candidates/"
                    + candidate
                    + "/approve"))
        .andExpect(method(POST))
        .andExpect(header(DownstreamHeaders.SERVICE_NAME, "admin-api"))
        .andExpect(header(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT))
        .andExpect(header("Idempotency-Key", key.toString()))
        .andExpect(headerDoesNotExist(DownstreamHeaders.INTERNAL_SERVICE))
        .andExpect(content().string(""))
        .andRespond(
            withSuccess(
                """
                {"idempotencyKey":"%s","outcome":"CANDIDATE_APPROVED","replay":false}
                """
                    .formatted(key),
                MediaType.APPLICATION_JSON));

    SettlementCandidateReceipt receipt =
        new SettlementClient(builder.build()).approveCandidate(candidate, key);

    assertThat(receipt.idempotencyKey()).isEqualTo(key);
    assertThat(receipt.outcome()).isEqualTo(SettlementCandidateReceipt.Outcome.CANDIDATE_APPROVED);
    assertThat(receipt.replay()).isFalse();
    server.verify();
  }

  private static RestClient.Builder settlementBuilder() {
    return RestClient.builder()
        .baseUrl("http://settlement.test")
        .defaultHeader(DownstreamHeaders.SERVICE_NAME, DownstreamHeaders.ADMIN_API)
        .defaultHeader(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT);
  }
}
