package com.sportsbook.admin.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.RiskLimitPayload;
import com.sportsbook.admin.client.RiskLimitType;
import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.protocol.value.Currency;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ResponseStatus;

class RiskAdminControllerContractTest {

  @Test
  void permitsEveryRoleToReadWithoutAuditingTheRead() throws NoSuchMethodException {
    Method read = RiskAdminController.class.getMethod("getLimits", UUID.class);

    assertThat(read.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAnyRole('ADMIN','TRADER','CS','READONLY')");
    assertThat(read.getAnnotation(Audited.class)).isNull();
  }

  @Test
  void restrictsAndAuditsBothMutations() throws NoSuchMethodException {
    Method update =
        RiskAdminController.class.getMethod(
            "setLimit", UUID.class, RiskLimitPayload.class, AdminContext.class);
    Method clear =
        RiskAdminController.class.getMethod(
            "clearLimit", UUID.class, RiskLimitType.class, Currency.class, AdminContext.class);

    assertMutation(update, AdminAction.RISK_LIMIT_UPDATE);
    assertMutation(clear, AdminAction.RISK_LIMIT_CLEAR);
    assertThat(update.getAnnotation(Audited.class).target())
        .isEqualTo("#userId + ':' + #body.type()");
    assertThat(clear.getAnnotation(Audited.class).target()).isEqualTo("#userId + ':' + #type");
  }

  private static void assertMutation(Method method, AdminAction action) {
    assertThat(method.getAnnotation(PreAuthorize.class).value())
        .isEqualTo("hasAnyRole('ADMIN','TRADER')");
    assertThat(method.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(method.getAnnotation(Audited.class).action()).isEqualTo(action);
  }
}
