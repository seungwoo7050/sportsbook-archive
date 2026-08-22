package com.sportsbook.settlement.result;

import com.sportsbook.protocol.domain.SettlementResult;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AcceptedResultRepository {

  private final JdbcTemplate jdbc;

  public AcceptedResultRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<AcceptedResult> findByEventId(UUID eventId) {
    return jdbc.query(
        """
        select m.event_id, m.accepted_candidate_id, m.mode, m.settled_at,
            s.selection_id, s.outcome
        from match_result m
        left join match_selection_result s on s.event_id = m.event_id
        where m.event_id = ? and m.accepted_candidate_id is not null
        order by s.selection_id
        """,
        result -> {
          if (!result.next()) {
            return Optional.empty();
          }
          UUID acceptedEventId = result.getObject("event_id", UUID.class);
          UUID candidateId = result.getObject("accepted_candidate_id", UUID.class);
          MatchOutcomeMode mode = MatchOutcomeMode.valueOf(result.getString("mode"));
          var sourceSettledAt = result.getTimestamp("settled_at").toInstant();
          var outcomes = new LinkedHashMap<UUID, SettlementResult>();
          do {
            UUID selectionId = result.getObject("selection_id", UUID.class);
            if (selectionId != null) {
              outcomes.put(selectionId, SettlementResult.valueOf(result.getString("outcome")));
            }
          } while (result.next());
          return Optional.of(
              new AcceptedResult(acceptedEventId, candidateId, mode, outcomes, sourceSettledAt));
        },
        eventId);
  }
}
