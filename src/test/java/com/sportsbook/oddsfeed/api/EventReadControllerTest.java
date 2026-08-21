package com.sportsbook.oddsfeed.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.oddsfeed.config.InternalSecurityProperties;
import com.sportsbook.oddsfeed.provider.EventSummary;
import com.sportsbook.oddsfeed.provider.Sport;
import com.sportsbook.oddsfeed.security.SecurityConfig;
import com.sportsbook.protocol.event.EventLifecycleStatus;
import com.sportsbook.protocol.value.EventId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EventReadController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(InternalSecurityProperties.class)
class EventReadControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockBean private EventCatalog catalog;

  @Test
  void listsEventsWithinTheRequestedPageSize() throws Exception {
    EventSummary first = summary(1, "2026-06-01T18:00:00Z");
    EventSummary second = summary(2, "2026-06-02T18:00:00Z");
    when(catalog.orderedByKickoff()).thenReturn(List.of(first, second));

    mockMvc
        .perform(get("/api/v1/events").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].eventId").value(first.eventId().value().toString()));
  }

  @Test
  void pageSizeUsesDefaultAndMaximumBoundaries() {
    assertThat(EventReadController.clampSize(0)).isEqualTo(20);
    assertThat(EventReadController.clampSize(-1)).isEqualTo(20);
    assertThat(EventReadController.clampSize(40)).isEqualTo(40);
    assertThat(EventReadController.clampSize(101)).isEqualTo(100);
  }

  @Test
  void cursorNavigatesToTheFollowingPage() throws Exception {
    EventSummary first = summary(1, "2026-06-01T18:00:00Z");
    EventSummary second = summary(2, "2026-06-02T18:00:00Z");
    EventSummary third = summary(3, "2026-06-03T18:00:00Z");
    when(catalog.orderedByKickoff()).thenReturn(List.of(first, second, third));

    String body =
        mockMvc
            .perform(get("/api/v1/events").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextCursor").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

    mockMvc
        .perform(
            get("/api/v1/events")
                .param("size", "2")
                .param("cursor", extractJsonValue(body, "nextCursor")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].eventId").value(third.eventId().value().toString()))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  @Test
  void rejectsMalformedCursor() throws Exception {
    when(catalog.orderedByKickoff()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/events").param("cursor", "not-a-cursor"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void cursorEncodingRoundTrips() {
    EventSummary summary = summary(11, "2026-06-01T18:00:00Z");

    EventReadController.Cursor cursor =
        EventReadController.decodeCursor(EventReadController.encodeCursor(summary));

    assertThat(cursor.kickoff()).isEqualTo(summary.scheduledStartAt());
    assertThat(cursor.eventId()).isEqualTo(summary.eventId().value());
  }

  @Test
  void returnsCurrentEvent() throws Exception {
    EventSummary summary = summary(7, "2026-06-01T18:00:00Z");
    when(catalog.get(summary.eventId())).thenReturn(Optional.of(summary));

    mockMvc
        .perform(get("/api/v1/events/{id}", summary.eventId().value()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventId").value(summary.eventId().value().toString()));
  }

  @Test
  void returnsNotFoundForMissingEvent() throws Exception {
    UUID eventId = UUID.randomUUID();
    when(catalog.get(new EventId(eventId))).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/events/{id}", eventId)).andExpect(status().isNotFound());
  }

  private static EventSummary summary(int seed, String kickoff) {
    return new EventSummary(
        new EventId(new UUID(0L, seed)),
        Sport.FOOTBALL,
        "Premier League",
        "Home" + seed,
        "Away" + seed,
        Instant.parse(kickoff),
        EventLifecycleStatus.SCHEDULED);
  }

  private static String extractJsonValue(String body, String key) {
    int field = body.indexOf("\"" + key + "\":");
    int start = body.indexOf('"', field + key.length() + 3) + 1;
    return body.substring(start, body.indexOf('"', start));
  }
}
