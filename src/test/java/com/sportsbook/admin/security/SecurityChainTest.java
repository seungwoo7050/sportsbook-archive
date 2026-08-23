package com.sportsbook.admin.security;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
      "admin.downstream.credentials.wallet-api-key=wallet-admin-test-key-000000000001",
      "admin.downstream.credentials.risk-api-key=risk-admin-test-key-00000000000002",
      "admin.downstream.credentials.odds-feed-api-key=odds-admin-test-key-00000000000003",
      "admin.downstream.credentials.settlement-api-key=settlement-admin-test-key-000000004"
    })
@AutoConfigureMockMvc
@AutoConfigureObservability
@Import(SecurityChainTest.RoleProbeController.class)
class SecurityChainTest {

  private static final String ADMIN_ONLY = "/admin/v1/test/admin-only";

  @DynamicPropertySource
  static void jwtKey(DynamicPropertyRegistry registry) {
    registry.add("admin.security.jwt.public-key", TestJwtKeys::publicKeyPem);
  }

  @Autowired private MockMvc mvc;

  @Test
  void rejectsAnonymousRequestsWithAnRfc7807Response() throws Exception {
    mvc.perform(get(ADMIN_ONLY))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void rejectsAnAuthenticatedRoleWithoutMethodAuthority() throws Exception {
    mvc.perform(get(ADMIN_ONLY).header(AUTHORIZATION, TestJwtKeys.bearer("reader-1", "READONLY")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
  }

  @Test
  void permitsTheRequiredMethodRole() throws Exception {
    mvc.perform(get(ADMIN_ONLY).header(AUTHORIZATION, TestJwtKeys.bearer("admin-1", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(content().string("ok"));
  }

  @Test
  void exposesOnlyThePrometheusScrapeEndpointOutsideHealth() throws Exception {
    mvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/plain"));

    mvc.perform(
            get("/actuator/metrics")
                .header(AUTHORIZATION, TestJwtKeys.bearer("admin-1", "ADMIN")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/actuator/env")
                .header(AUTHORIZATION, TestJwtKeys.bearer("admin-1", "ADMIN")))
        .andExpect(status().isForbidden());
  }

  @RestController
  static class RoleProbeController {

    @GetMapping(ADMIN_ONLY)
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
      return "ok";
    }
  }
}
