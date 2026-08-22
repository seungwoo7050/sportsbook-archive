package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.admin.client.RiskClient;
import com.sportsbook.admin.client.RiskLimitPayload;
import com.sportsbook.admin.client.RiskLimitType;
import com.sportsbook.admin.client.RiskLimitsResponse;
import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import com.sportsbook.protocol.value.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskAdminControllerDelegationTest {

  @Test
  void delegatesEveryLimitOperationWithoutChangingItsScope() {
    RiskClient risk = mock(RiskClient.class);
    UUID userId = UUID.fromString("018f0000-0000-7000-8000-000000000126");
    RiskLimitsResponse snapshot = new RiskLimitsResponse(userId, List.of());
    AdminContext context =
        new AdminContext(
            "operator-1",
            AdminRole.TRADER,
            UUID.fromString("018f0000-0000-7000-8000-000000000127"),
            "trace-1");
    RiskLimitPayload update = new RiskLimitPayload(RiskLimitType.STAKE_DAILY, Currency.KRW, 750L);
    when(risk.getLimits(userId)).thenReturn(snapshot);
    RiskAdminController controller = new RiskAdminController(risk);

    assertThat(controller.getLimits(userId)).isSameAs(snapshot);
    controller.setLimit(userId, update, context);
    controller.clearLimit(userId, RiskLimitType.STAKE_WEEKLY, Currency.USD, context);

    verify(risk).getLimits(userId);
    verify(risk).setLimit(userId, update);
    verify(risk).clearLimit(userId, RiskLimitType.STAKE_WEEKLY, Currency.USD);
  }
}
