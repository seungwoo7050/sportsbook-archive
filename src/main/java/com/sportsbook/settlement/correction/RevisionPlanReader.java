package com.sportsbook.settlement.correction;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RevisionPlanReader {

  private final JdbcTemplate jdbc;

  public RevisionPlanReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public Optional<RevisionPlan> find(UUID revisionId) {
    Optional<RevisionPlanRow> row =
        jdbc
            .query(
                "select * from settlement_revision where revision_id = ?",
                (result, rowNumber) -> RevisionPlanRow.read(result),
                revisionId)
            .stream()
            .findFirst();
    if (row.isEmpty()) {
      return Optional.empty();
    }
    List<ResolvedSelection> selections =
        jdbc.query(
            """
            select selection_id, leg_index, odds, outcome
            from settlement_revision_selection where revision_id = ? order by leg_index
            """,
            (result, rowNumber) -> {
              if (result.getInt("leg_index") != rowNumber) {
                throw new IllegalStateException("Revision selection order is not contiguous");
              }
              return new ResolvedSelection(
                  result.getObject("selection_id", UUID.class),
                  Odds.ofDecimal(result.getBigDecimal("odds")),
                  SettlementResult.valueOf(result.getString("outcome")));
            },
            revisionId);
    if (selections.isEmpty()) {
      throw new IllegalStateException("Persisted revision has no selection snapshot");
    }
    return Optional.of(row.orElseThrow().toPlan(selections));
  }
}
