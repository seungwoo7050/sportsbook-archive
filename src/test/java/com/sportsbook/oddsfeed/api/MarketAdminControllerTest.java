package com.sportsbook.oddsfeed.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.oddsfeed.config.InternalSecurityProperties;
import com.sportsbook.oddsfeed.delivery.OperatorActionQueue;
import com.sportsbook.oddsfeed.security.InternalApiKeyAuthenticationFilter;
import com.sportsbook.oddsfeed.security.SecurityConfig;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(controllers = MarketAdminController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(InternalSecurityProperties.class)
class MarketAdminControllerTest {

  private static final String API_KEY = "test-internal-api-key-0123456789abcdef";
  private static final Instant NOW = Instant.parse("2026-05-28T10:00:00Z");

  @Autowired private MockMvc mockMvc;
  @MockBean private OperatorActionQueue queue;
  @MockBean private Clock clock;

  @BeforeEach
  void setUpClock() {
    when(clock.instant()).thenReturn(NOW);
  }

  @Test
  void authenticatedControlsReturnOnlyAfterDurableSubmission() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID marketId = UUID.randomUUID();
    UUID suspendId = UUID.randomUUID();
    UUID closeId = UUID.randomUUID();
    UUID reopenId = UUID.randomUUID();

    perform(eventId, marketId, suspendId, "suspend").andExpect(status().isAccepted());
    perform(eventId, marketId, closeId, "close").andExpect(status().isAccepted());
    perform(eventId, marketId, reopenId, "reopen").andExpect(status().isAccepted());

    verify(queue)
        .submit(
            any(),
            eq(suspendId),
            eq(new EventId(eventId)),
            eq(new MarketId(marketId)),
            eq(MarketStatus.SUSPENDED),
            eq("incident"),
            eq(NOW));
    verify(queue).submit(any(), eq(closeId), any(), any(), eq(MarketStatus.CLOSED), any(), any());
    verify(queue).submit(any(), eq(reopenId), any(), any(), eq(MarketStatus.OPEN), any(), any());
  }

  private ResultActions perform(UUID eventId, UUID marketId, UUID actionId, String action)
      throws Exception {
    String path = "/internal/v1/events/" + eventId + "/markets/" + marketId + "/" + action;
    return mockMvc.perform(
        post(path)
            .header(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, "admin-api")
            .header(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY)
            .header(MarketAdminController.IDEMPOTENCY_HEADER, "operator-request")
            .header(MarketAdminController.ACTION_ID_HEADER, actionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"incident\"}"));
  }
}
