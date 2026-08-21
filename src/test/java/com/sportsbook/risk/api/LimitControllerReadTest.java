package com.sportsbook.risk.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideField;
import com.sportsbook.risk.limit.LimitOverrideStore;
import com.sportsbook.risk.limit.LimitResolver;
import com.sportsbook.risk.policy.RiskLimitProperties;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class LimitControllerReadTest {
  private static final UUID USER_VALUE = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UserId USER = UserId.of(USER_VALUE);

  @Mock private LimitOverrideStore overrides;
  private LimitResolver resolver;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    resolver = new LimitResolver(new RiskLimitProperties(null, null, null, null, 0), overrides);
    mvc = MockMvcBuilders.standaloneSetup(new LimitController(overrides, resolver)).build();
  }

  @Test
  void returnsEveryEffectiveLimitAndItsSource() throws Exception {
    when(overrides.find(any(), any())).thenReturn(OptionalLong.empty());
    var dailyKrw = LimitOverrideField.monetary(LimitType.STAKE_DAILY, Currency.KRW);
    when(overrides.find(eq(USER), eq(dailyKrw))).thenReturn(OptionalLong.of(750L));

    mvc.perform(get("/internal/v1/risk/limits/" + USER_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_VALUE.toString()))
        .andExpect(jsonPath("$.limits.length()").value(7))
        .andExpect(jsonPath("$.limits[0].source").value("OVERRIDE"))
        .andExpect(jsonPath("$.limits[6].type").value("SELECTIONS_PER_MINUTE"))
        .andExpect(jsonPath("$.limits[6].currency").doesNotExist());
  }
}
