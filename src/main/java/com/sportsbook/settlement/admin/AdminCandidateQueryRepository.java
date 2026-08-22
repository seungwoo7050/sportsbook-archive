package com.sportsbook.settlement.admin;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminCandidateQueryRepository {

  private final JdbcTemplate jdbc;

  public AdminCandidateQueryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Optional<View> find(UUID candidateId) {
    return jdbc
        .query(
            """
            select c.candidate_id, c.event_id, c.mode, c.settled_at, c.received_at,
                c.state, c.replaces_candidate_id, c.decision_reason, c.decided_at,
                coalesce(m.accepted_candidate_id = c.candidate_id, false) as accepted
            from result_candidate c
            left join match_result m on m.event_id = c.event_id
            where c.candidate_id = ?
            """,
            (result, rowNumber) ->
                new View(
                    result.getObject("candidate_id", UUID.class),
                    result.getObject("event_id", UUID.class),
                    result.getString("mode"),
                    result.getTimestamp("settled_at").toInstant(),
                    result.getTimestamp("received_at").toInstant(),
                    result.getString("state"),
                    result.getObject("replaces_candidate_id", UUID.class),
                    result.getString("decision_reason"),
                    result.getTimestamp("decided_at") == null
                        ? null
                        : result.getTimestamp("decided_at").toInstant(),
                    result.getBoolean("accepted")),
            candidateId)
        .stream()
        .findFirst();
  }

  public record View(
      UUID candidateId,
      UUID eventId,
      String mode,
      Instant settledAt,
      Instant receivedAt,
      String state,
      UUID replacesCandidateId,
      String decisionReason,
      Instant decidedAt,
      boolean accepted) {}
}
