package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.admin.audit.AuditLogRepository;
import com.sportsbook.admin.audit.AuditWriteRepository;
import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.context.AdminContextArgumentResolver;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
  private static final String MUTATION = "/admin/v1/test/mutation";

  @DynamicPropertySource
  static void jwtKey(DynamicPropertyRegistry registry) {
    registry.add("admin.security.jwt.public-key", TestJwtKeys::publicKeyPem);
  }

  @Autowired private MockMvc mvc;

  @MockBean private AuditLogRepository auditLogs;

  @MockBean private AuditWriteRepository auditWrites;

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

  @Test
  void initializesOneMutationIdentityAfterJwtAuthentication() throws Exception {
    var response =
        mvc.perform(post(MUTATION).header(AUTHORIZATION, TestJwtKeys.bearer("admin-1", "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().exists(AdminContextArgumentResolver.ACTION_ID_HEADER))
            .andReturn()
            .getResponse();

    assertThat(response.getContentAsString())
        .isEqualTo(response.getHeader(AdminContextArgumentResolver.ACTION_ID_HEADER));
  }

  @Test
  void assignsAUuid7ActionIdBeforeMutationBodyBindingFails() throws Exception {
    String actionId =
        mvc.perform(
                post("/admin/v1/wallet/018f0000-0000-7000-8000-000000000187/refund")
                    .header(AUTHORIZATION, TestJwtKeys.bearer("cs-1", "CS"))
                    .header("Idempotency-Key", "raw refund key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(header().exists(AdminContextArgumentResolver.ACTION_ID_HEADER))
            .andReturn()
            .getResponse()
            .getHeader(AdminContextArgumentResolver.ACTION_ID_HEADER);

    assertThat(UUID.fromString(actionId).version()).isEqualTo(7);
  }

  @RestController
  static class RoleProbeController {

    @GetMapping(ADMIN_ONLY)
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
      return "ok";
    }

    @PostMapping(MUTATION)
    @PreAuthorize("hasRole('ADMIN')")
    String mutate(AdminContext context) {
      return context.actionId().toString();
    }
  }
}
