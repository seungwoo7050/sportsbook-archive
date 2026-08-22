package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.SettlementCandidateReceipt;
import com.sportsbook.admin.client.SettlementClient;
import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

class SettlementCandidateApprovalControllerTest {

  private static final UUID CANDIDATE = UUID.fromString("018f0000-0000-7000-8000-000000000172");
  private static final UUID KEY = UUID.fromString("018f0000-0000-7000-8000-000000000173");

  @Test
  void delegatesTheTypedApprovalAndReturnsItsReceipt() throws NoSuchMethodException {
    SettlementClient settlements = mock(SettlementClient.class);
    SettlementCandidateReceipt receipt =
        new SettlementCandidateReceipt(
            KEY, SettlementCandidateReceipt.Outcome.CANDIDATE_APPROVED, false);
    when(settlements.approveCandidate(CANDIDATE, KEY)).thenReturn(receipt);

    SettlementCandidateReceipt result =
        new SettlementCandidateCommandController(settlements)
            .approve(CANDIDATE, context(), requestWithKey());

    assertThat(result).isSameAs(receipt);
    verify(settlements).approveCandidate(CANDIDATE, KEY);
    Method method =
        SettlementCandidateCommandController.class.getMethod(
            "approve", UUID.class, AdminContext.class, HttpServletRequest.class);
    assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/approve");
    assertThat(method.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAnyRole('ADMIN','TRADER')");
    Audited audited = method.getAnnotation(Audited.class);
    assertThat(audited.action()).isEqualTo(AdminAction.RESULT_CANDIDATE_APPROVE);
    assertThat(audited.target()).isEqualTo("#candidateId");
    assertThat(audited.reason()).isEmpty();
  }

  private static MockHttpServletRequest requestWithKey() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(AdminRequestHeaders.IDEMPOTENCY_KEY, KEY.toString());
    return request;
  }

  private static AdminContext context() {
    return new AdminContext("operator-1", AdminRole.TRADER, UUID.randomUUID(), "trace-1");
  }
}
