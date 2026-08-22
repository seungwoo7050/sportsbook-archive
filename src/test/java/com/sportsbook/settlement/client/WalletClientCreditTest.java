package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
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

class WalletClientCreditTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef";

  @Test
  void postsAuthorizedCreditAndAcceptsAdditionalSuccessFields() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    WalletClient client =
        new WalletClient(
            builder,
            new WalletEndpointProperties(URI.create("http://wallet.test")),
            new WalletAuthenticationHeaders(new WalletCredentials(SECRET)));
    UUID userId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    server
        .expect(once(), requestTo("http://wallet.test" + WalletClient.CREDIT_PATH))
        .andExpect(method(POST))
        .andExpect(header("X-Internal-Service", "settlement-service"))
        .andExpect(header("X-Internal-Api-Key", SECRET))
        .andExpect(header("Idempotency-Key", "settlement:refund:test"))
        .andExpect(
            content()
                .json(
                    """
                    {"userId":"%s","amount":{"amount":1500,"currency":"KRW"},
                     "source":"USER_LOCKED","reason":"REFUND"}
                    """
                        .formatted(userId)))
        .andRespond(
            withSuccess(
                ("""
                 {"operationGroupId":"%s","userId":"%s",
                  "amount":{"amount":1500,"currency":"KRW"},"reason":"BET_REFUND",
                  "at":"2026-08-22T00:00:00Z","extra":"allowed"}
                 """)
                    .formatted(operationId, userId),
                MediaType.APPLICATION_JSON));

    UUID result =
        client.credit(
            "settlement:refund:test", userId, Money.krw(1500), WalletCreditPurpose.RETURNED_STAKE);

    assertThat(result).isEqualTo(operationId);
    server.verify();
  }

  @Test
  void rejectsWalletBaseUrlsThatAreNotBareHttpOrigins() {
    List.of(
            "wallet.test",
            "mailto:wallet@test",
            "http://user:secret@wallet.test",
            "http://wallet.test/path",
            "http://wallet.test?query=yes",
            "http://wallet.test#fragment")
        .forEach(
            value ->
                assertThatIllegalArgumentException()
                    .isThrownBy(() -> new WalletEndpointProperties(URI.create(value))));
  }
}
