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

class SettlementClientCandidateRejectionTest {

  @Test
  void postsTheExactRejectionWithSettlementAuthentication() {
    UUID candidate = UUID.fromString("018f0000-0000-7000-8000-000000000165");
    UUID key = UUID.fromString("018f0000-0000-7000-8000-000000000166");
    RestClient.Builder builder = settlementBuilder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(
            requestTo(
                "http://settlement.test/internal/admin/result-candidates/" + candidate + "/reject"))
        .andExpect(method(POST))
        .andExpect(header(DownstreamHeaders.SERVICE_NAME, "admin-api"))
        .andExpect(header(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT))
        .andExpect(header("Idempotency-Key", key.toString()))
        .andExpect(headerDoesNotExist(DownstreamHeaders.INTERNAL_SERVICE))
        .andExpect(content().json("{\"reason\":\"bad result\"}", true))
        .andRespond(
            withSuccess(
                """
                {"idempotencyKey":"%s","outcome":"CANDIDATE_REJECTED","replay":true}
                """
                    .formatted(key),
                MediaType.APPLICATION_JSON));

    SettlementCandidateReceipt receipt =
        new SettlementClient(builder.build())
            .rejectCandidate(candidate, key, new SettlementRejectionPayload("  bad result  "));

    assertThat(receipt.outcome()).isEqualTo(SettlementCandidateReceipt.Outcome.CANDIDATE_REJECTED);
    assertThat(receipt.replay()).isTrue();
    server.verify();
  }

  private static RestClient.Builder settlementBuilder() {
    return RestClient.builder()
        .baseUrl("http://settlement.test")
        .defaultHeader(DownstreamHeaders.SERVICE_NAME, DownstreamHeaders.ADMIN_API)
        .defaultHeader(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT);
  }
}
