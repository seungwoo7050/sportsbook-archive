package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sportsbook.protocol.value.Money;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WalletMalformedSuccessTest {

  @Test
  void treatsMissingOperationIdentityAsTransientMalformedSuccess() {
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
        .andRespond(withSuccess("{\"extra\":\"allowed\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () ->
                client.credit(
                    "settlement:refund:test",
                    UUID.randomUUID(),
                    Money.krw(100),
                    WalletCreditPurpose.RETURNED_STAKE))
        .isInstanceOf(WalletFailurePolicy.TransientFailure.class)
        .hasMessageContaining("WALLET_MALFORMED_RESPONSE");
    server.verify();
  }
}
