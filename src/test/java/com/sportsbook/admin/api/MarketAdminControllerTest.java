package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.MarketClient;
import com.sportsbook.admin.client.MarketStatusPayload;
import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import com.sportsbook.protocol.value.IdempotencyKey;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ResponseStatus;

class MarketAdminControllerTest {

  private static final UUID EVENT =
      UUID.fromString("018f0000-0000-7000-8000-000000000135");
  private static final UUID MARKET =
      UUID.fromString("018f0000-0000-7000-8000-000000000136");
  private static final UUID ACTION =
      UUID.fromString("018f0000-0000-7000-8000-000000000137");

  @Test
  void delegatesSuspensionWithTheAttemptIdentity() {
    MarketClient markets = mock(MarketClient.class);
    MarketStatusPayload body = new MarketStatusPayload("feed investigation");
    AdminContext context = new AdminContext("operator-1", AdminRole.TRADER, ACTION, "trace-1");
    MockHttpServletRequest request = requestWithKey();

    new MarketAdminController(markets).suspend(EVENT, MARKET, body, context, request);

    verify(markets)
        .changeStatus(
            EVENT,
            MARKET,
            MarketClient.Action.SUSPEND,
            body,
            IdempotencyKey.of("market action 01"),
            ACTION);
  }

  @Test
  void guardsAndAuditsSuspension() throws NoSuchMethodException {
    Method method = actionMethod("suspend");

    assertThat(method.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAnyRole('ADMIN','TRADER')");
    assertThat(method.getAnnotation(ResponseStatus.class).value())
        .isEqualTo(HttpStatus.ACCEPTED);
    Audited audited = method.getAnnotation(Audited.class);
    assertThat(audited.action()).isEqualTo(AdminAction.MARKET_SUSPEND);
    assertThat(audited.target()).isEqualTo("#eventId + '/' + #marketId");
    assertThat(audited.reason()).isEqualTo("#body.reason()");
  }

  @Test
  void delegatesGuardsAndAuditsClosure() throws NoSuchMethodException {
    MarketClient markets = mock(MarketClient.class);
    MarketStatusPayload body = new MarketStatusPayload("event completed");
    AdminContext context = new AdminContext("operator-1", AdminRole.ADMIN, ACTION, "trace-1");

    new MarketAdminController(markets)
        .close(EVENT, MARKET, body, context, requestWithKey());

    verify(markets)
        .changeStatus(
            EVENT,
            MARKET,
            MarketClient.Action.CLOSE,
            body,
            IdempotencyKey.of("market action 01"),
            ACTION);
    Method method = actionMethod("close");
    assertThat(method.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAnyRole('ADMIN','TRADER')");
    assertThat(method.getAnnotation(ResponseStatus.class).value())
        .isEqualTo(HttpStatus.ACCEPTED);
    assertThat(method.getAnnotation(Audited.class).action())
        .isEqualTo(AdminAction.MARKET_CLOSE);
  }

  private static Method actionMethod(String name) throws NoSuchMethodException {
    return MarketAdminController.class.getMethod(
        name,
        UUID.class,
        UUID.class,
        MarketStatusPayload.class,
        AdminContext.class,
        HttpServletRequest.class);
  }

  private static MockHttpServletRequest requestWithKey() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(AdminRequestHeaders.IDEMPOTENCY_KEY, "market action 01");
    return request;
  }
}
