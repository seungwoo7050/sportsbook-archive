package com.sportsbook.settlement.correction;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CorrectionTargetRepository {

  private final JdbcTemplate jdbc;

  public CorrectionTargetRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<UUID> findActionable(UUID eventId, UUID candidateId, int limit) {
    if (limit < 1 || limit > 1000) {
      throw new IllegalArgumentException("Correction batch size is outside bounds");
    }
    return jdbc.query(
        """
        select distinct b.bet_id from bet b
        join bet_selection s on s.bet_id = b.bet_id
        where b.status = 'SETTLED' and s.event_id = ?
            and (s.source_candidate_id is null or s.source_candidate_id <> ?)
            and not exists (select 1 from settlement_revision r
                where r.bet_id = b.bet_id
                  and r.revision_number = b.revision_number + 1)
        order by b.bet_id limit ?
        """,
        (result, rowNumber) -> result.getObject("bet_id", UUID.class),
        eventId,
        candidateId,
        limit);
  }
}
