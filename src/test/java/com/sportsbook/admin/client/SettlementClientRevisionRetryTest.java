package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SettlementClientRevisionRetryTest {

  @Test
  void postsAnEmptyRetryAndRequiresAnAcceptedReceipt() {
    UUID revision = UUID.fromString("018f0000-0000-7000-8000-000000000169");
    UUID key = UUID.fromString("018f0000-0000-7000-8000-000000000170");
    RestClient.Builder builder = settlementBuilder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://settlement.test/internal/admin/revisions/" + revision + "/retry"))
        .andExpect(method(POST))
        .andExpect(header(DownstreamHeaders.SERVICE_NAME, "admin-api"))
        .andExpect(header(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT))
        .andExpect(header("Idempotency-Key", key.toString()))
        .andExpect(headerDoesNotExist(DownstreamHeaders.INTERNAL_SERVICE))
        .andExpect(content().string(""))
        .andRespond(
            withStatus(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    """
                    {"idempotencyKey":"%s","outcome":"QUEUED","revisionState":"PENDING",
                     "attemptCount":0,"nextRetryAt":"2026-08-22T01:05:00Z"}
                    """
                        .formatted(key)));

    SettlementRetryReceipt receipt =
        new SettlementClient(builder.build()).retryRevision(revision, key);

    assertThat(receipt.outcome()).isEqualTo(SettlementRetryReceipt.Outcome.QUEUED);
    assertThat(receipt.revisionState()).isEqualTo(SettlementRevisionView.State.PENDING);
    server.verify();
  }

  private static RestClient.Builder settlementBuilder() {
    return RestClient.builder()
        .baseUrl("http://settlement.test")
        .defaultHeader(DownstreamHeaders.SERVICE_NAME, DownstreamHeaders.ADMIN_API)
        .defaultHeader(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT);
  }
}
