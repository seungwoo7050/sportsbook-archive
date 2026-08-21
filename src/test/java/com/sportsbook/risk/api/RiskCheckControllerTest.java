package com.sportsbook.risk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.service.LimitRejection;
import com.sportsbook.risk.service.RiskCheckCommand;
import com.sportsbook.risk.service.RiskCheckOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RiskCheckControllerTest {
  private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
  private static final UserId USER =
      UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
  private static final BetId BET =
      BetId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"));
  private static final SelectionId SELECTION =
      SelectionId.of(UUID.fromString("00000000-0000-0000-0000-000000000003"));
  @Mock private Function<RiskCheckCommand, RiskCheckOutcome> check;
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    var controller = new RiskCheckController(check, Clock.fixed(NOW, ZoneOffset.UTC));
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
  }

  @Test
  void mapsTypedCandidateAtTheInjectedTime() throws Exception {
    when(check.apply(any())).thenReturn(RiskCheckOutcome.approved(List.of()));
    mvc.perform(post("/internal/v1/risk/check").contentType("application/json").content(body(100)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approved").value(true));

    var command = ArgumentCaptor.forClass(RiskCheckCommand.class);
    verify(check).apply(command.capture());
    assertThat(command.getValue())
        .isEqualTo(new RiskCheckCommand(USER, BET, Money.krw(100), List.of(SELECTION), NOW));
  }

  @Test
  void rendersTheFirstLimitRejection() throws Exception {
    var rejection = LimitRejection.rolling(LimitType.STAKE_DAILY, Currency.KRW, 900, 1000, 101);
    when(check.apply(any())).thenReturn(RiskCheckOutcome.rejectedByLimit(rejection));

    mvc.perform(post("/internal/v1/risk/check").contentType("application/json").content(body(101)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rejectionReason").value("STAKE_DAILY_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.limit.type").value("STAKE_DAILY"));
  }

  @Test
  void rejectsDuplicateSelectionsBeforeCallingTheService() throws Exception {
    var request = new RiskCheckRequest(USER, BET, Money.krw(100), List.of(SELECTION, SELECTION));
    mvc.perform(
            post("/internal/v1/risk/check")
                .contentType("application/json")
                .content(json.writeValueAsBytes(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verify(check, never()).apply(any());
  }

  private byte[] body(long amount) throws Exception {
    return json.writeValueAsBytes(
        new RiskCheckRequest(USER, BET, Money.krw(amount), List.of(SELECTION)));
  }
}
