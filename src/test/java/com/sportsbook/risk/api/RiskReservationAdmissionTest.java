package com.sportsbook.risk.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.reservation.ReservationDecision;
import com.sportsbook.risk.reservation.ReservationState;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RiskReservationAdmissionTest {
  private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
  private static final Instant EXPIRES = NOW.plusSeconds(120);
  private static final UserId USER =
      UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BetId BET =
      BetId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final SelectionId SELECTION =
      SelectionId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));

  @Mock private RiskReservationController.Operations operations;
  private final ObjectMapper json =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    var controller = new RiskReservationController(operations, Clock.fixed(NOW, ZoneOffset.UTC));
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .build();
  }

  @Test
  void returnsTheOpaqueLeaseToken() throws Exception {
    when(operations.reserve(any()))
        .thenReturn(
            ReservationDecision.approved(
                ReservationState.RESERVED, EXPIRES, "opaque-token", false, List.of()));
    perform()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approved").value(true))
        .andExpect(jsonPath("$.expiresAt").value(EXPIRES.toString()))
        .andExpect(jsonPath("$.reservationToken").value("opaque-token"));
  }

  @Test
  void returnsDurableDeclinesWithoutAToken() throws Exception {
    when(operations.reserve(any()))
        .thenReturn(ReservationDecision.rejected("STAKE_DAILY_LIMIT_EXCEEDED", true, List.of()));
    perform()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approved").value(false))
        .andExpect(jsonPath("$.replayed").value(true))
        .andExpect(jsonPath("$.reservationToken").doesNotExist());
  }

  @Test
  void mapsChangedPayloadsToDuplicateBet() throws Exception {
    when(operations.reserve(any())).thenReturn(ReservationDecision.conflict());
    perform()
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("DUPLICATE_BET"));
  }

  private org.springframework.test.web.servlet.ResultActions perform() throws Exception {
    var request = new RiskCheckRequest(USER, BET, Money.krw(100), List.of(SELECTION));
    return mvc.perform(
        post("/internal/v1/risk/reservations")
            .contentType("application/json")
            .content(json.writeValueAsBytes(request)));
  }
}
