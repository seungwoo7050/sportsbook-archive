package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

class AuditDeclaredStatusClassifierTest {

  private final AuditOutcomeClassifier classifier = new AuditOutcomeClassifier();

  @Test
  void recordsDeclaredAndDefaultSuccessStatuses() throws Exception {
    assertStatus(Probe.class.getDeclaredMethod("accepted"), 202);
    assertStatus(Probe.class.getDeclaredMethod("noContent"), 204);
    assertStatus(Probe.class.getDeclaredMethod("ok"), 200);
  }

  private void assertStatus(Method method, int expectedStatus) {
    AuditOutcomeClassifier.AuditDecision decision = classifier.result(null, method);
    assertThat(decision.outcome()).isEqualTo(AuditOutcome.SUCCESS);
    assertThat(decision.httpStatus()).isEqualTo(expectedStatus);
  }

  static class Probe {

    @ResponseStatus(HttpStatus.ACCEPTED)
    void accepted() {}

    @ResponseStatus(HttpStatus.NO_CONTENT)
    void noContent() {}

    void ok() {}
  }
}
