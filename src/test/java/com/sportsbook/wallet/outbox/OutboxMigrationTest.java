package com.sportsbook.wallet.outbox;

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
class OutboxMigrationTest {
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
  void definesCommittedStreamPositionsAndOneEventPerOperation() {
    Map<String, String> constraints =
        jdbc
            .query(
                """
                SELECT conname, pg_get_constraintdef(oid) AS definition
                FROM pg_constraint
                WHERE conrelid IN ('outbox_stream'::regclass, 'outbox_event'::regclass)
                """,
                (result, row) -> Map.entry(result.getString(1), result.getString(2)))
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    assertThat(constraints.get("outbox_stream_pkey"))
        .contains("PRIMARY KEY (topic, partition_key)");
    assertThat(constraints.get("fk_outbox_stream"))
        .contains("FOREIGN KEY (topic, partition_key)")
        .contains("outbox_stream(topic, partition_key)");
    assertThat(constraints.get("uq_outbox_stream_sequence"))
        .contains("UNIQUE (topic, partition_key, stream_sequence)");
    assertThat(constraints.get("uq_outbox_operation")).contains("UNIQUE (operation_key)");
    assertThat(constraints.get("fk_outbox_operation")).contains("DEFERRABLE INITIALLY DEFERRED");
    assertThat(constraints).doesNotContainKey("ck_outbox_timestamps");
    assertThat(
            jdbc.queryForObject(
                "SELECT column_default FROM information_schema.columns "
                    + "WHERE table_name='outbox_event' AND column_name='available_at'",
                String.class))
        .contains("clock_timestamp()");
    assertThat(
            jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname='ix_outbox_fifo'", String.class))
        .contains("topic, partition_key, stream_sequence")
        .contains("published_at IS NULL");
  }
}
