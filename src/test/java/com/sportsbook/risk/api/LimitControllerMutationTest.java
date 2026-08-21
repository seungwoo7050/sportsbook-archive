package com.sportsbook.risk.api;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideField;
import com.sportsbook.risk.limit.LimitOverrideStore;
import com.sportsbook.risk.limit.LimitResolver;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.policy.SafeRedisNumber;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LimitControllerMutationTest {
  private static final UUID USER_VALUE = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UserId USER = UserId.of(USER_VALUE);

  @Mock private LimitOverrideStore overrides;
  private final ObjectMapper json = new ObjectMapper();
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    var resolver = new LimitResolver(new RiskLimitProperties(null, null, null, null, 0), overrides);
    mvc =
        MockMvcBuilders.standaloneSetup(new LimitController(overrides, resolver))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
  }

  @Test
  void setsAndClearsTypedOverrides() throws Exception {
    var update = new LimitUpdateRequest(LimitType.STAKE_DAILY, Currency.KRW, 750L);
    mvc.perform(
            patch("/internal/v1/risk/limits/" + USER_VALUE)
                .contentType("application/json")
                .content(json.writeValueAsBytes(update)))
        .andExpect(status().isNoContent());
    verify(overrides)
        .set(USER, LimitOverrideField.monetary(LimitType.STAKE_DAILY, Currency.KRW), 750L);

    mvc.perform(delete("/internal/v1/risk/limits/" + USER_VALUE + "/SELECTIONS_PER_MINUTE"))
        .andExpect(status().isNoContent());
    verify(overrides).clear(USER, LimitOverrideField.selections());
  }

  @Test
  void rejectsScopeMismatchAndUnsafeValues() throws Exception {
    var wrongScope = new LimitUpdateRequest(LimitType.STAKE_WEEKLY, null, 100L);
    mvc.perform(
            patch("/internal/v1/risk/limits/" + USER_VALUE)
                .contentType("application/json")
                .content(json.writeValueAsBytes(wrongScope)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    String unsafe =
        "{\"type\":\"STAKE_DAILY\",\"currency\":\"KRW\",\"value\":"
            + (SafeRedisNumber.MAX_VALUE + 1)
            + "}";
    mvc.perform(
            patch("/internal/v1/risk/limits/" + USER_VALUE)
                .contentType("application/json")
                .content(unsafe))
        .andExpect(status().isBadRequest());
    verify(overrides, never())
        .set(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyLong());
  }

  @Test
  void rejectsDeleteTargetsWithTheWrongCurrencyScope() throws Exception {
    mvc.perform(delete("/internal/v1/risk/limits/" + USER_VALUE + "/STAKE_DAILY"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    mvc.perform(
            delete("/internal/v1/risk/limits/" + USER_VALUE + "/SELECTIONS_PER_MINUTE")
                .queryParam("currency", "KRW"))
        .andExpect(status().isBadRequest());
  }
}
