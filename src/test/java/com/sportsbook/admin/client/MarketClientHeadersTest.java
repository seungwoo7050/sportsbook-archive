package com.sportsbook.admin.client;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.sportsbook.protocol.value.IdempotencyKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MarketClientHeadersTest {

  @Test
  void preservesTheLogicalKeyWhileEachAttemptUsesItsOwnActionId() {
    UUID eventId = UUID.fromString("018f0000-0000-7000-8000-000000000131");
    UUID marketId = UUID.fromString("018f0000-0000-7000-8000-000000000132");
    UUID firstAction = UUID.fromString("018f0000-0000-7000-8000-000000000133");
    UUID retryAction = UUID.fromString("018f0000-0000-7000-8000-000000000134");
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl("http://odds.test")
            .defaultHeader(DownstreamHeaders.INTERNAL_SERVICE, DownstreamHeaders.ADMIN_API)
            .defaultHeader(DownstreamHeaders.INTERNAL_API_KEY, ClientIsolationFixture.ODDS);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    expectClose(server, eventId, marketId, firstAction);
    expectClose(server, eventId, marketId, retryAction);
    MarketClient client = new MarketClient(builder.build());
    MarketStatusPayload reason = new MarketStatusPayload("  feed investigation  ");
    IdempotencyKey key = IdempotencyKey.of("market close retry 01");

    client.changeStatus(eventId, marketId, MarketClient.Action.CLOSE, reason, key, firstAction);
    client.changeStatus(eventId, marketId, MarketClient.Action.CLOSE, reason, key, retryAction);

    server.verify();
  }

  private static void expectClose(
      MockRestServiceServer server, UUID eventId, UUID marketId, UUID actionId) {
    server
        .expect(
            requestTo(
                "http://odds.test/internal/v1/events/"
                    + eventId
                    + "/markets/"
                    + marketId
                    + "/close"))
        .andExpect(method(POST))
        .andExpect(header(DownstreamHeaders.INTERNAL_SERVICE, "admin-api"))
        .andExpect(header(DownstreamHeaders.INTERNAL_API_KEY, ClientIsolationFixture.ODDS))
        .andExpect(header("Idempotency-Key", "market close retry 01"))
        .andExpect(header(DownstreamHeaders.ADMIN_ACTION_ID, actionId.toString()))
        .andExpect(content().json("{\"reason\":\"feed investigation\"}"))
        .andRespond(withStatus(HttpStatus.ACCEPTED));
  }
}
