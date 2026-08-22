package com.sportsbook.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.audit.AuditLogRepository;
import com.sportsbook.admin.audit.AuditWriteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
      "management.endpoint.health.validate-group-membership=false",
      "admin.security.jwt.public-key=test-key",
      "admin.downstream.credentials.wallet-api-key=wallet-admin-test-key-000000000001",
      "admin.downstream.credentials.risk-api-key=risk-admin-test-key-00000000000002",
      "admin.downstream.credentials.odds-feed-api-key=odds-admin-test-key-00000000000003",
      "admin.downstream.credentials.settlement-api-key=settlement-admin-test-key-000000004"
    })
class AdminApiApplicationTest {

  @Autowired private ApplicationContext applicationContext;

  @MockBean private JwtDecoder jwtDecoder;

  @MockBean private AuditLogRepository auditLogs;

  @MockBean private AuditWriteRepository auditWrites;

  @Test
  void startsTheAdminApplicationContext() {
    assertThat(applicationContext.getBean(AdminApiApplication.class)).isNotNull();
  }
}
