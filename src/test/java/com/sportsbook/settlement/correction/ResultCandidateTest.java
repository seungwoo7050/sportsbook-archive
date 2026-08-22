package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResultCandidateTest {

  @Test
  void pendingCandidateOwnsAnImmutableFullSnapshot() {
    UUID selectionId = UUID.randomUUID();
    Map<UUID, SettlementResult> outcomes = new HashMap<>();
    outcomes.put(selectionId, SettlementResult.WON);
    UUID replaced = UUID.randomUUID();

    ResultCandidate candidate =
        ResultCandidate.pending(
            UUID.randomUUID(),
            "a".repeat(64),
            MatchOutcomeMode.COMPLETED,
            outcomes,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(1),
            replaced);
    outcomes.put(selectionId, SettlementResult.LOST);

    assertThat(candidate.state()).isEqualTo(ResultCandidateState.PENDING);
    assertThat(candidate.sequence()).isNull();
    assertThat(candidate.replacesCandidateId()).isEqualTo(replaced);
    assertThat(candidate.outcomes()).containsEntry(selectionId, SettlementResult.WON);
    assertThat(candidate.outcomes()).isUnmodifiable();
  }
}
