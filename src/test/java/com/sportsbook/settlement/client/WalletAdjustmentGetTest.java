package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WalletAdjustmentGetTest {

  @Test
  void retrievesAppliedProofByCanonicalRevisionIdentity() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    WalletClient client =
        new WalletClient(
            builder,
            new WalletEndpointProperties(URI.create("http://wallet.test")),
            new WalletAuthenticationHeaders(
                new WalletCredentials("0123456789abcdef0123456789abcdef")));
    UUID revisionId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    server
        .expect(requestTo("http://wallet.test" + WalletClient.ADJUSTMENT_PATH + "/" + revisionId))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                proofJson(revisionId, betId, userId, operationId), MediaType.APPLICATION_JSON));

    WalletAdjustmentProof proof = client.findAdjustment(revisionId);

    assertThat(proof.status()).isEqualTo(WalletAdjustmentProof.Status.APPLIED);
    assertThat(proof.operationGroupId()).isEqualTo(operationId);
    assertThat(proof.appliedAt()).isNotNull();
    server.verify();
  }

  private static String proofJson(UUID revisionId, UUID betId, UUID userId, UUID operationId) {
    return """
        {"revisionId":"%s","betId":"%s","revisionNumber":1,"userId":"%s",
         "previousPayout":{"amount":1000,"currency":"KRW"},
         "newPayout":{"amount":2000,"currency":"KRW"},"deltaAmount":1000,"currency":"KRW",
         "status":"APPLIED","queueSequence":null,"operationGroupId":"%s",
         "queuedAt":null,"appliedAt":"2026-08-22T00:00:00Z","nextAttemptAt":null,
         "extra":"allowed"}
        """
        .formatted(revisionId, betId, userId, operationId);
  }
}
