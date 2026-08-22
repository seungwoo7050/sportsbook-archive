package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.admin.security.AdminRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AuditLogRepositoryTest {

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private AuditLogRepository repository;
  @Autowired private TestEntityManager entities;

  @Test
  void persistsAndReadsNullableStartedLifecycleEvidence() {
    UUID actionId = UUID.fromString("018f0000-0000-7000-8000-000000000011");
    Instant startedAt = Instant.parse("2026-08-23T00:00:00Z");
    repository.saveAndFlush(
        new AuditLogEntity(
            actionId,
            "operator-1",
            AdminRole.ADMIN,
            "WALLET_REFUND",
            "user-1",
            AuditOutcome.STARTED,
            null,
            "refund",
            "trace-1",
            startedAt,
            null));
    entities.clear();

    AuditLogEntity found = repository.findById(actionId).orElseThrow();
    assertThat(found.getOutcome()).isEqualTo(AuditOutcome.STARTED);
    assertThat(found.getHttpStatus()).isNull();
    assertThat(found.getStartedAt()).isEqualTo(startedAt);
    assertThat(found.getCompletedAt()).isNull();
  }
}
