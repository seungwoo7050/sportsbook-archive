package com.sportsbook.risk.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.risk.reservation.ReservationTransition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RiskReservationTransitionTest {
  private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
  private static final UUID BET_VALUE = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final BetId BET = BetId.of(BET_VALUE);
  private static final String TOKEN = "a".repeat(64);
  private static final String OTHER_TOKEN = "b".repeat(64);

  @Mock private RiskReservationController.Operations operations;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    var controller = new RiskReservationController(operations, Clock.fixed(NOW, ZoneOffset.UTC));
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
  }

  @Test
  void commitRequiresAndForwardsTheOpaqueToken() throws Exception {
    when(operations.commit(BET, TOKEN, NOW)).thenReturn(ReservationTransition.APPLIED);
    mvc.perform(put(path("/commit")).header(RiskReservationController.TOKEN_HEADER, TOKEN))
        .andExpect(status().isNoContent());
    verify(operations).commit(BET, TOKEN, NOW);

    mvc.perform(put(path("/commit")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    mvc.perform(put(path("/commit")).header(RiskReservationController.TOKEN_HEADER, " "))
        .andExpect(status().isBadRequest());
    mvc.perform(put(path("/commit")).header(RiskReservationController.TOKEN_HEADER, "opaque-token"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void commitDistinguishesMissingAndConflictingReservations() throws Exception {
    when(operations.commit(eq(BET), eq(TOKEN), eq(NOW))).thenReturn(ReservationTransition.EXPIRED);
    when(operations.commit(eq(BET), eq(OTHER_TOKEN), eq(NOW)))
        .thenReturn(ReservationTransition.CONFLICT);
    mvc.perform(put(path("/commit")).header(RiskReservationController.TOKEN_HEADER, TOKEN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("RISK_RESERVATION_NOT_FOUND"));
    mvc.perform(put(path("/commit")).header(RiskReservationController.TOKEN_HEADER, OTHER_TOKEN))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("DUPLICATE_BET"));
  }

  @Test
  void releaseIsIdempotentUntilTheReservationIsCommitted() throws Exception {
    when(operations.release(BET, NOW))
        .thenReturn(ReservationTransition.NOT_FOUND, ReservationTransition.CONFLICT);
    mvc.perform(delete(path(""))).andExpect(status().isNoContent());
    mvc.perform(delete(path("")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("RISK_RESERVATION_COMMITTED"));
  }

  private String path(String suffix) {
    return "/internal/v1/risk/reservations/" + BET_VALUE + suffix;
  }
}
