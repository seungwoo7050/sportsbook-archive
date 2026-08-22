package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sportsbook.admin.client.RiskClient;
import com.sportsbook.admin.client.RiskLimitType;
import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import com.sportsbook.protocol.value.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskAdminControllerScopeTest {

  private static final UUID USER = UUID.fromString("018f0000-0000-7000-8000-000000000179");
  private static final AdminContext CONTEXT =
      new AdminContext("operator-1", AdminRole.TRADER, UUID.randomUUID(), "trace-1");

  @Test
  void rejectsMissingAndForbiddenCurrencyScopesBeforeCallingRisk() {
    RiskClient risk = mock(RiskClient.class);
    RiskAdminController controller = new RiskAdminController(risk);

    assertThatThrownBy(() -> controller.clearLimit(USER, RiskLimitType.STAKE_DAILY, null, CONTEXT))
        .isInstanceOf(AdminRequestException.class);
    assertThatThrownBy(
            () ->
                controller.clearLimit(
                    USER, RiskLimitType.SELECTIONS_PER_MINUTE, Currency.KRW, CONTEXT))
        .isInstanceOf(AdminRequestException.class);
    verifyNoInteractions(risk);
  }
}
