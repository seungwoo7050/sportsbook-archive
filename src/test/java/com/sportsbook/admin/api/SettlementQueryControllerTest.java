package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.SettlementClient;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class SettlementQueryControllerTest {

  private static final UUID TARGET = UUID.fromString("018f0000-0000-7000-8000-000000000171");

  @Test
  void exposesCandidateEvidenceToEveryAdminRoleWithoutMutationAudit() throws NoSuchMethodException {
    SettlementClient settlements = mock(SettlementClient.class);

    new SettlementQueryController(settlements).getCandidate(TARGET);

    verify(settlements).getCandidate(TARGET);
    assertReadContract("getCandidate", "/result-candidates/{candidateId}");
  }

  @Test
  void exposesRevisionEvidenceToEveryAdminRoleWithoutMutationAudit() throws NoSuchMethodException {
    SettlementClient settlements = mock(SettlementClient.class);

    new SettlementQueryController(settlements).getRevision(TARGET);

    verify(settlements).getRevision(TARGET);
    assertReadContract("getRevision", "/revisions/{revisionId}");
  }

  private static void assertReadContract(String methodName, String path)
      throws NoSuchMethodException {
    assertThat(SettlementQueryController.class.getAnnotation(RequestMapping.class).value())
        .containsExactly("/admin/v1/settlements");
    Method method = SettlementQueryController.class.getMethod(methodName, UUID.class);
    assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(path);
    assertThat(method.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAnyRole('ADMIN','TRADER','CS','READONLY')");
    assertThat(method.getAnnotation(Audited.class)).isNull();
  }
}
