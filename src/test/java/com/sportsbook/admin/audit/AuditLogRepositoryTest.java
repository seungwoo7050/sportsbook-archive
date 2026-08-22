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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

  @Test
  void filtersByActorAndTimeBeforePagingNewestFirst() {
    Instant origin = Instant.parse("2026-08-23T01:00:00Z");
    repository.saveAllAndFlush(
        java.util.List.of(
            terminal(21, "operator-1", origin.plusSeconds(60)),
            terminal(22, "operator-2", origin.plusSeconds(120)),
            terminal(23, "operator-1", origin.plusSeconds(180)),
            terminal(24, "operator-1", origin.plusSeconds(240))));
    entities.clear();
    PageRequest newestFirst = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "startedAt"));

    var first = repository.search(origin, origin.plusSeconds(240), "operator-1", newestFirst);
    var second =
        repository.search(origin, origin.plusSeconds(240), "operator-1", newestFirst.next());
    var literalInjection =
        repository.search(origin, origin.plusSeconds(240), "operator-1' OR '1'='1", newestFirst);

    assertThat(first.getTotalElements()).isEqualTo(2);
    assertThat(first.getContent())
        .extracting(AuditLogEntity::getActionId)
        .containsExactly(actionId(23));
    assertThat(second.getContent())
        .extracting(AuditLogEntity::getActionId)
        .containsExactly(actionId(21));
    assertThat(literalInjection).isEmpty();
  }

  private static AuditLogEntity terminal(int suffix, String actorId, Instant startedAt) {
    return new AuditLogEntity(
        actionId(suffix),
        actorId,
        AdminRole.ADMIN,
        "MARKET_CLOSE",
        "market-1",
        AuditOutcome.SUCCESS,
        202,
        "operator request",
        "trace-1",
        startedAt,
        startedAt.plusSeconds(1));
  }

  private static UUID actionId(int suffix) {
    return UUID.fromString("018f0000-0000-7000-8000-0000000000" + suffix);
  }
}
