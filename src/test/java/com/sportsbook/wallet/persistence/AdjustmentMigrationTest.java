package com.sportsbook.wallet.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdjustmentMigrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void definesDurableProofAndFifoConstraints() {
    Map<String, String> constraints =
        jdbc
            .query(
                """
                SELECT conname, pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'wallet_adjustment'::regclass
                """,
                (result, row) -> Map.entry(result.getString(1), result.getString(2)))
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertThat(constraints.get("uq_wallet_adjustment_bet_revision"))
        .contains("UNIQUE (bet_id, revision_number)");
    assertThat(constraints.get("uq_wallet_adjustment_user_sequence"))
        .contains("UNIQUE (user_id, queue_sequence)");
    assertThat(constraints.get("fk_wallet_adjustment_operation"))
        .contains("DEFERRABLE INITIALLY DEFERRED");
    assertThat(constraints.get("fk_wallet_adjustment_operation_group"))
        .contains("FOREIGN KEY (idempotency_key, operation_group_id)")
        .contains("DEFERRABLE INITIALLY DEFERRED");
    assertThat(constraints.keySet()).noneMatch(name -> name.contains("account"));
    assertThat(constraints.get("ck_wallet_adjustment_request"))
        .contains("revision_number >= 1")
        .contains("settlement:revision:")
        .contains("00000000-0000-7000-8000-000000000001")
        .contains("delta_amount = (new_payout_amount - previous_payout_amount)");
    assertThat(constraints.get("ck_wallet_adjustment_outcome"))
        .contains("delta_amount < 0")
        .contains("retry_count = 0");
    assertThat(constraints).doesNotContainKey("ck_wallet_adjustment_timestamps");
    assertThat(index("ix_wallet_adjustment_fifo"))
        .contains("user_id, queue_sequence")
        .contains("status", "'BLOCKED'");
    assertThat(index("ix_wallet_adjustment_due"))
        .contains("next_attempt_at, user_id")
        .contains("status", "'BLOCKED'");
  }

  private String index(String name) {
    return jdbc.queryForObject(
        "SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, name);
  }
}
