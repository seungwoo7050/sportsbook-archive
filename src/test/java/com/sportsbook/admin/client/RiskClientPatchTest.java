package com.sportsbook.admin.client;

import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

import com.sportsbook.protocol.value.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RiskClientPatchTest {

  @Test
  void replacesTheExactTypedCurrencyLimit() {
    UUID userId = UUID.fromString("018f0000-0000-7000-8000-000000000124");
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl("http://risk.test")
            .defaultHeader(DownstreamHeaders.INTERNAL_SERVICE, DownstreamHeaders.ADMIN_API)
            .defaultHeader(DownstreamHeaders.INTERNAL_API_KEY, ClientIsolationFixture.RISK);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://risk.test/internal/v1/risk/limits/" + userId))
        .andExpect(method(PATCH))
        .andExpect(header(DownstreamHeaders.INTERNAL_SERVICE, "admin-api"))
        .andExpect(header(DownstreamHeaders.INTERNAL_API_KEY, ClientIsolationFixture.RISK))
        .andExpect(content().json("{\"type\":\"STAKE_DAILY\",\"currency\":\"KRW\",\"value\":750}"))
        .andRespond(withNoContent());

    new RiskClient(builder.build())
        .setLimit(userId, new RiskLimitPayload(RiskLimitType.STAKE_DAILY, Currency.KRW, 750L));

    server.verify();
  }
}
