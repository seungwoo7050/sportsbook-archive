package com.sportsbook.admin.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.admin.security.TestJwtKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "spring.kafka.bootstrap-servers=127.0.0.1:1",
      "admin.audit.stale-scan-interval=PT1H",
      "admin.downstream.credentials.wallet-api-key=wallet-admin-test-key-000000000001",
      "admin.downstream.credentials.risk-api-key=risk-admin-test-key-00000000000002",
      "admin.downstream.credentials.odds-feed-api-key=odds-admin-test-key-00000000000003",
      "admin.downstream.credentials.settlement-api-key=settlement-admin-test-key-000000004"
    })
@AutoConfigureMockMvc
@Testcontainers
class DatabaseReadinessTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void dependencies(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("admin.security.jwt.public-key", TestJwtKeys::publicKeyPem);
  }

  @Autowired private MockMvc mvc;

  @Test
  void staysReadyWithoutKafkaAndFailsWhenPostgresqlStops() throws Exception {
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());

    POSTGRES.stop();

    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("DOWN"))
        .andExpect(jsonPath("$.components").doesNotExist());
  }
}
