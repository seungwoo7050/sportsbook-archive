package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.SettlementClient;
import com.sportsbook.admin.client.SettlementRetryReceipt;
import com.sportsbook.admin.client.SettlementRevisionView;
import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ResponseStatus;

class SettlementRevisionCommandControllerTest {

  private static final UUID REVISION = UUID.fromString("018f0000-0000-7000-8000-000000000176");
  private static final UUID KEY = UUID.fromString("018f0000-0000-7000-8000-000000000177");

  @Test
  void delegatesGuardsAndAuditsAnAcceptedRetry() throws NoSuchMethodException {
    SettlementClient settlements = mock(SettlementClient.class);
    SettlementRetryReceipt receipt =
        new SettlementRetryReceipt(
            KEY,
            SettlementRetryReceipt.Outcome.QUEUED,
            SettlementRevisionView.State.PENDING,
            0,
            Instant.parse("2026-08-22T01:05:00Z"));
    when(settlements.retryRevision(REVISION, KEY)).thenReturn(receipt);

    SettlementRetryReceipt result =
        new SettlementRevisionCommandController(settlements)
            .retry(REVISION, context(), requestWithKey());

    assertThat(result).isSameAs(receipt);
    verify(settlements).retryRevision(REVISION, KEY);
    Method method =
        SettlementRevisionCommandController.class.getMethod(
            "retry", UUID.class, AdminContext.class, HttpServletRequest.class);
    assertThat(method.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(method.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAnyRole('ADMIN','TRADER')");
    Audited audited = method.getAnnotation(Audited.class);
    assertThat(audited.action()).isEqualTo(AdminAction.SETTLEMENT_REVISION_RETRY);
    assertThat(audited.target()).isEqualTo("#revisionId");
  }

  private static MockHttpServletRequest requestWithKey() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(AdminRequestHeaders.IDEMPOTENCY_KEY, KEY.toString());
    return request;
  }

  private static AdminContext context() {
    return new AdminContext("operator-1", AdminRole.ADMIN, UUID.randomUUID(), "trace-1");
  }
}
