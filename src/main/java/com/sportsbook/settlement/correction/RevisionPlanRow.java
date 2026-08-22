package com.sportsbook.settlement.correction;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.settlement.domain.SlipKind;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record RevisionPlanRow(
    UUID revisionId,
    UUID betId,
    long revisionNumber,
    UUID userId,
    UUID eventId,
    UUID sourceCandidateId,
    SettlementResult previousResult,
    long previousPayout,
    SettlementResult newResult,
    long newPayout,
    Currency currency,
    SlipKind slipKind,
    Integer systemMinWins,
    Integer systemTotalSelections,
    long unitStake,
    Instant sourceResultSettledAt,
    Instant createdAt) {

  static RevisionPlanRow read(ResultSet row) throws SQLException {
    return new RevisionPlanRow(
        row.getObject("revision_id", UUID.class),
        row.getObject("bet_id", UUID.class),
        row.getLong("revision_number"),
        row.getObject("user_id", UUID.class),
        row.getObject("event_id", UUID.class),
        row.getObject("source_candidate_id", UUID.class),
        SettlementResult.valueOf(row.getString("previous_result")),
        row.getLong("previous_payout_amount"),
        SettlementResult.valueOf(row.getString("new_result")),
        row.getLong("new_payout_amount"),
        Currency.valueOf(row.getString("currency")),
        SlipKind.valueOf(row.getString("slip_type")),
        (Integer) row.getObject("system_min_wins"),
        (Integer) row.getObject("system_total_selections"),
        row.getLong("unit_stake_amount"),
        row.getTimestamp("source_result_settled_at").toInstant(),
        row.getTimestamp("created_at").toInstant());
  }

  RevisionPlan toPlan(List<ResolvedSelection> selections) {
    int selectionCount = selections.size();
    boolean validShape =
        switch (slipKind) {
          case SINGLE -> selectionCount == 1;
          case MULTIPLE -> selectionCount >= 2;
          case SYSTEM -> systemTotalSelections != null && selectionCount == systemTotalSelections;
        };
    if (!validShape) {
      throw new IllegalStateException("Persisted revision selection shape is inconsistent");
    }
    RevisionTarget target =
        new RevisionTarget(
            betId,
            revisionNumber,
            userId,
            eventId,
            sourceCandidateId,
            previousResult,
            new Money(previousPayout, currency),
            slipKind.toProtocol(systemMinWins, systemTotalSelections),
            new Money(unitStake, currency),
            selections,
            sourceResultSettledAt);
    return new RevisionPlan(
        revisionId, target, newResult, new Money(newPayout, currency), createdAt);
  }
}
