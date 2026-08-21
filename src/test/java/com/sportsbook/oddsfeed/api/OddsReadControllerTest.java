package com.sportsbook.oddsfeed.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.oddsfeed.cache.RedisOddsCache;
import com.sportsbook.oddsfeed.config.InternalSecurityProperties;
import com.sportsbook.oddsfeed.security.InternalApiKeyAuthenticationFilter;
import com.sportsbook.oddsfeed.security.SecurityConfig;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OddsReadController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(InternalSecurityProperties.class)
class OddsReadControllerTest {

  private static final String API_KEY = "test-internal-api-key-0123456789abcdef";

  @Autowired private MockMvc mockMvc;
  @MockBean private RedisOddsCache cache;

  @Test
  void returnsCurrentOdds() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID marketId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    when(cache.getOdds(new EventId(eventId), new MarketId(marketId), new SelectionId(selectionId)))
        .thenReturn(Optional.of(Odds.ofDecimal("1.85")));

    mockMvc
        .perform(get("/api/v1/odds/{e}/{m}/{s}", eventId, marketId, selectionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventId").value(eventId.toString()))
        .andExpect(jsonPath("$.marketId").value(marketId.toString()))
        .andExpect(jsonPath("$.selectionId").value(selectionId.toString()))
        .andExpect(jsonPath("$.odds").value(1.85));
  }

  @Test
  void returnsNotFoundForMissingOdds() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID marketId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    when(cache.getOdds(new EventId(eventId), new MarketId(marketId), new SelectionId(selectionId)))
        .thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/odds/{e}/{m}/{s}", eventId, marketId, selectionId))
        .andExpect(status().isNotFound());
  }

  @Test
  void internalRoutesRequireTheAdminCaller() throws Exception {
    String path = "/internal/v1/events/{event}/markets/{market}/suspend";
    UUID eventId = UUID.randomUUID();
    UUID marketId = UUID.randomUUID();

    mockMvc.perform(post(path, eventId, marketId)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post(path, eventId, marketId)
                .header(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, "settlement-service")
                .header(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post(path, eventId, marketId)
                .header(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, "admin-api")
                .header(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/internal/v1/unknown")
                .header(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, "admin-api")
                .header(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, API_KEY))
        .andExpect(status().isForbidden());
  }
}
