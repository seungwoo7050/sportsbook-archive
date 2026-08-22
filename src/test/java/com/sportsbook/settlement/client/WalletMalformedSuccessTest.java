package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sportsbook.protocol.value.Money;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WalletMalformedSuccessTest {

  @Test
  void treatsIncompleteOrMismatchedOperationProofsAsTransient() {
    UUID userId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    String exact =
        """
        {"operationGroupId":"%s","userId":"%s",
         "amount":{"amount":100,"currency":"KRW"},"reason":"BET_REFUND",
         "at":"2026-08-22T00:00:00Z"}
        """
            .formatted(operationId, userId);
    List.of(
            "{\"extra\":\"allowed\"}",
            exact.replace(userId.toString(), UUID.randomUUID().toString()),
            exact.replace("\"amount\":100", "\"amount\":101"),
            exact.replace("KRW", "USD"),
            exact.replace("BET_REFUND", "BET_PAYOUT"),
            exact.replace("\"at\":\"2026-08-22T00:00:00Z\"", "\"at\":null"))
        .forEach(body -> assertMalformed(userId, body));
  }

  private static void assertMalformed(UUID userId, String body) {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    WalletClient client =
        new WalletClient(
            builder,
            new WalletEndpointProperties(URI.create("http://wallet.test")),
            new WalletAuthenticationHeaders(
                new WalletCredentials("0123456789abcdef0123456789abcdef")));
    server
        .expect(requestTo("http://wallet.test" + WalletClient.CREDIT_PATH))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () ->
                client.credit(
                    "settlement:refund:test",
                    userId,
                    Money.krw(100),
                    WalletCreditPurpose.RETURNED_STAKE))
        .isInstanceOf(WalletFailurePolicy.TransientFailure.class)
        .hasMessageContaining("WALLET_MALFORMED_RESPONSE");
    server.verify();
  }
}
