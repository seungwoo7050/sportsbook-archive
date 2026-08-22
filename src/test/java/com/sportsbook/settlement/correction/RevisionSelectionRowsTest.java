package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionSelectionRowsTest {

  @Test
  void mapsOrderedSnapshotValuesForBatchPersistence() {
    UUID revisionId = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    RevisionSnapshot snapshot =
        new RevisionSnapshot(
            "MULTIPLE",
            null,
            null,
            100,
            List.of(
                new RevisionSnapshot.Selection(
                    first, 0, new BigDecimal("2.0000"), SettlementResult.WON),
                new RevisionSnapshot.Selection(
                    second, 1, new BigDecimal("1.5000"), SettlementResult.PUSH)));

    List<Object[]> rows = RevisionSelectionRows.from(revisionId, snapshot);

    assertThat(rows)
        .containsExactly(
            new Object[] {revisionId, first, 0, new BigDecimal("2.0000"), "WON"},
            new Object[] {revisionId, second, 1, new BigDecimal("1.5000"), "PUSH"});
  }
}
