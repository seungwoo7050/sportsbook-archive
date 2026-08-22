package com.sportsbook.admin.client;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

import com.sportsbook.protocol.value.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RiskClientDeleteTest {

  @Test
  void sendsCurrencyOnlyForMonetaryTargets() {
    UUID userId = UUID.fromString("018f0000-0000-7000-8000-000000000125");
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl("http://risk.test")
            .defaultHeader(DownstreamHeaders.INTERNAL_SERVICE, DownstreamHeaders.ADMIN_API)
            .defaultHeader(DownstreamHeaders.INTERNAL_API_KEY, ClientIsolationFixture.RISK);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(
            requestTo(
                "http://risk.test/internal/v1/risk/limits/"
                    + userId
                    + "/STAKE_WEEKLY?currency=USD"))
        .andExpect(method(DELETE))
        .andExpect(header(DownstreamHeaders.INTERNAL_SERVICE, "admin-api"))
        .andExpect(header(DownstreamHeaders.INTERNAL_API_KEY, ClientIsolationFixture.RISK))
        .andRespond(withNoContent());
    server
        .expect(
            requestTo(
                "http://risk.test/internal/v1/risk/limits/" + userId + "/SELECTIONS_PER_MINUTE"))
        .andExpect(method(DELETE))
        .andRespond(withNoContent());
    RiskClient client = new RiskClient(builder.build());

    client.clearLimit(userId, RiskLimitType.STAKE_WEEKLY, Currency.USD);
    client.clearLimit(userId, RiskLimitType.SELECTIONS_PER_MINUTE, null);

    server.verify();
  }
}
