package com.sportsbook.settlement.result;

import static com.sportsbook.settlement.persistence.JdbcTimestamps.required;
import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class AcceptedResultRepositoryTest {

  @Test
  void readsTheAcceptedCandidateAndCanonicalOutcomeOrder() {
    JdbcTemplate jdbc = jdbc();
    UUID eventId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    Instant settledAt = Instant.parse("2026-08-22T00:00:00Z");
    jdbc.update(
        "insert into match_result values (?, 'COMPLETED', ?, ?)",
        eventId,
        required(settledAt),
        candidateId);
    jdbc.update("insert into match_selection_result values (?, ?, ?)", eventId, second, "LOST");
    jdbc.update("insert into match_selection_result values (?, ?, ?)", eventId, first, "WON");

    AcceptedResult result = new AcceptedResultRepository(jdbc).findByEventId(eventId).orElseThrow();

    assertThat(result.candidateId()).isEqualTo(candidateId);
    assertThat(result.sourceSettledAt()).isEqualTo(settledAt);
    assertThat(result.outcomes().keySet()).containsExactly(first, second);
    assertThat(result.outcomes().values())
        .containsExactly(SettlementResult.WON, SettlementResult.LOST);
  }

  private static JdbcTemplate jdbc() {
    var dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        """
        create table match_result (
            event_id uuid primary key, mode varchar(16) not null,
            settled_at timestamp with time zone not null, accepted_candidate_id uuid)
        """);
    jdbc.execute(
        """
        create table match_selection_result (
            event_id uuid not null, selection_id uuid not null, outcome varchar(8) not null,
            primary key (event_id, selection_id))
        """);
    return jdbc;
  }
}
