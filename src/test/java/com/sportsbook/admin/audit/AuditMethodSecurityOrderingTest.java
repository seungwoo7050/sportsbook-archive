package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.security.AdminRole;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditMethodSecurityOrderingTest {

  private static final UUID ACTION_ID = UUID.fromString("018f0000-0000-7000-8000-000000000071");

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void recordsAStartedAndFailedRowAroundAMethodAuthorizationDenial() {
    try (AnnotationConfigApplicationContext context =
        new AnnotationConfigApplicationContext(DeniedAuditConfiguration.class)) {
      AuditService audits = context.getBean(AuditService.class);
      SecuredOperations operations = context.getBean(SecuredOperations.class);
      UsernamePasswordAuthenticationToken reader =
          UsernamePasswordAuthenticationToken.authenticated(
              "reader-1", "n/a", java.util.List.of(new SimpleGrantedAuthority("ROLE_READONLY")));
      SecurityContextHolder.getContext().setAuthentication(reader);
      AdminContext adminContext =
          new AdminContext("reader-1", AdminRole.READONLY, ACTION_ID, "trace-1");

      assertThatThrownBy(() -> operations.close(adminContext))
          .isInstanceOf(AccessDeniedException.class);

      verify(audits)
          .begin(adminContext, AdminAction.MARKET_CLOSE.name(), "market-1", "operator request");
      verify(audits).complete(ACTION_ID, AuditOutcome.FAILED, 403);
      assertThat(operations.invocations()).hasValue(0);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAspectJAutoProxy
  @EnableMethodSecurity
  static class DeniedAuditConfiguration {

    @Bean
    AuditService auditService() {
      return mock(AuditService.class);
    }

    @Bean
    AuditAspect auditAspect(AuditService auditService) {
      return new AuditAspect(auditService);
    }

    @Bean
    SecuredOperations securedOperations() {
      return new SecuredOperations();
    }
  }

  static class SecuredOperations {

    private final AtomicInteger invocations = new AtomicInteger();

    @Audited(
        action = AdminAction.MARKET_CLOSE,
        target = "'market-1'",
        reason = "'operator request'")
    @PreAuthorize("hasRole('TRADER')")
    public void close(AdminContext context) {
      invocations.incrementAndGet();
    }

    AtomicInteger invocations() {
      return invocations;
    }
  }
}
