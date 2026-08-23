package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

class AuditedMetadataTest {

  @Test
  void retainsMethodMetadataForRuntimeInterception() throws Exception {
    assertThat(Audited.class.getAnnotation(Retention.class).value())
        .isEqualTo(RetentionPolicy.RUNTIME);
    assertThat(Audited.class.getAnnotation(Target.class).value())
        .containsExactly(ElementType.METHOD);

    Audited metadata = Probe.class.getDeclaredMethod("refund").getAnnotation(Audited.class);
    assertThat(metadata.action()).isEqualTo(AdminAction.WALLET_REFUND);
    assertThat(metadata.target()).isEqualTo("#p0");
    assertThat(metadata.reason()).isEqualTo("'operator request'");
  }

  static class Probe {

    @Audited(action = AdminAction.WALLET_REFUND, target = "#p0", reason = "'operator request'")
    void refund() {}
  }
}
