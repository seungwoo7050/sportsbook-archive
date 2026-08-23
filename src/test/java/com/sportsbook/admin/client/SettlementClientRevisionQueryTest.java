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

class SettlementClientRevisionQueryTest {

  @Test
  void fetchesAllRevisionEvidenceWithoutAnIdempotencyHeader() {
    UUID revision = UUID.fromString("018f0000-0000-7000-8000-000000000158");
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl("http://settlement.test")
            .defaultHeader(DownstreamHeaders.SERVICE_NAME, DownstreamHeaders.ADMIN_API)
            .defaultHeader(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://settlement.test/internal/admin/revisions/" + revision))
        .andExpect(method(GET))
        .andExpect(header(DownstreamHeaders.SERVICE_NAME, "admin-api"))
        .andExpect(header(DownstreamHeaders.API_KEY, ClientIsolationFixture.SETTLEMENT))
        .andExpect(headerDoesNotExist("Idempotency-Key"))
        .andRespond(withSuccess(response(revision), MediaType.APPLICATION_JSON));

    SettlementRevisionView result = new SettlementClient(builder.build()).getRevision(revision);

    assertThat(result.revisionId()).isEqualTo(revision);
    assertThat(result.state()).isEqualTo(SettlementRevisionView.State.BLOCKED);
    assertThat(result.walletStatus()).isEqualTo(SettlementRevisionView.WalletStatus.BLOCKED);
    server.verify();
  }

  private static String response(UUID revision) {
    return """
        {"revisionId":"%s","betId":"018f0000-0000-7000-8000-000000000159",
         "revisionNumber":2,"eventId":"018f0000-0000-7000-8000-000000000160",
         "sourceCandidateId":"018f0000-0000-7000-8000-000000000161",
         "state":"BLOCKED","attemptCount":3,"nextRetryAt":"2026-08-22T01:03:00Z",
         "lastErrorCode":"WALLET_BUSY","leaseUntil":null,"walletStatus":"BLOCKED",
         "walletQueueSequence":17,
         "walletOperationGroupId":null,
         "walletQueuedAt":"2026-08-22T01:01:00Z","walletAppliedAt":null,
         "walletNextAttemptAt":"2026-08-22T01:03:00Z",
         "createdAt":"2026-08-22T01:00:00Z","updatedAt":"2026-08-22T01:02:00Z",
         "appliedAt":null}
        """
        .formatted(revision);
  }
}
