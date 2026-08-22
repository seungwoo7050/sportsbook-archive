package com.sportsbook.settlement.correction;

import java.util.List;
import java.util.UUID;

final class RevisionSelectionRows {

  private RevisionSelectionRows() {}

  static List<Object[]> from(UUID revisionId, RevisionSnapshot snapshot) {
    return snapshot.selections().stream()
        .map(
            selection ->
                new Object[] {
                  revisionId,
                  selection.selectionId(),
                  selection.legIndex(),
                  selection.odds(),
                  selection.outcome().name()
                })
        .toList();
  }
}
