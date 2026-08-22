package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sportsbook.protocol.value.Money;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WalletClientForfeitTest {

  @Test
  void postsForfeitWithoutCreditSourceOrReason() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    WalletClient client =
        new WalletClient(
            builder,
            new WalletEndpointProperties(URI.create("http://wallet.test")),
            new WalletAuthenticationHeaders(
                new WalletCredentials("0123456789abcdef0123456789abcdef")));
    UUID userId = UUID.randomUUID();
    UUID operationId = UUID.randomUUID();
    server
        .expect(requestTo("http://wallet.test" + WalletClient.FORFEIT_PATH))
        .andExpect(method(POST))
        .andExpect(header("Idempotency-Key", "settlement:forfeit:test"))
        .andExpect(
            content()
                .json(
                    """
                    {"userId":"%s","amount":{"amount":3000,"currency":"KRW"}}
                    """
                        .formatted(userId),
                    true))
        .andRespond(
            withSuccess(
                "{\"operationGroupId\":\"%s\"}".formatted(operationId),
                MediaType.APPLICATION_JSON));

    UUID result = client.forfeit("settlement:forfeit:test", userId, Money.krw(3000));

    assertThat(result).isEqualTo(operationId);
    server.verify();
  }
}
