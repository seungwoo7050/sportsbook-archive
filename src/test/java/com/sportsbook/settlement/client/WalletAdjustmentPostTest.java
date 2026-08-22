package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;

import com.sportsbook.protocol.value.Money;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WalletAdjustmentPostTest {

  @Test
  void acceptsDurableBlockedProofWithExactRevisionKey() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    WalletClient client = client(builder);
    UUID revisionId = UUID.randomUUID();
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    server
        .expect(requestTo("http://wallet.test" + WalletClient.ADJUSTMENT_PATH))
        .andExpect(header("Idempotency-Key", "settlement:revision:" + revisionId))
        .andExpect(
            content()
                .json(
                    """
                    {"revisionId":"%s","betId":"%s","revisionNumber":2,"userId":"%s",
                     "previousPayout":{"amount":5000,"currency":"KRW"},
                     "newPayout":{"amount":3000,"currency":"KRW"}}
                    """
                        .formatted(revisionId, betId, userId)))
        .andRespond(
            withAccepted()
                .contentType(MediaType.APPLICATION_JSON)
                .body(proofJson(revisionId, betId, userId)));

    WalletAdjustmentProof proof =
        client.adjust(revisionId, betId, 2, userId, Money.krw(5000), Money.krw(3000));

    assertThat(proof.status()).isEqualTo(WalletAdjustmentProof.Status.BLOCKED);
    assertThat(proof.deltaAmount()).isEqualTo(-2000);
    assertThat(proof.queueSequence()).isEqualTo(17L);
    server.verify();
  }

  private static WalletClient client(RestClient.Builder builder) {
    return new WalletClient(
        builder,
        new WalletEndpointProperties(URI.create("http://wallet.test")),
        new WalletAuthenticationHeaders(new WalletCredentials("0123456789abcdef0123456789abcdef")));
  }

  private static String proofJson(UUID revisionId, UUID betId, UUID userId) {
    return """
        {"revisionId":"%s","betId":"%s","revisionNumber":2,"userId":"%s",
         "previousPayout":{"amount":5000,"currency":"KRW"},
         "newPayout":{"amount":3000,"currency":"KRW"},"deltaAmount":-2000,"currency":"KRW",
         "status":"BLOCKED","queueSequence":17,"operationGroupId":null,
         "queuedAt":"2026-08-22T00:00:00Z","appliedAt":null,
         "nextAttemptAt":"2026-08-22T00:00:01Z","extra":"allowed"}
        """
        .formatted(revisionId, betId, userId);
  }
}
