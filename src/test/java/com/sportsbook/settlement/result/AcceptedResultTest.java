package com.sportsbook.settlement.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcceptedResultTest {

  @Test
  void preservesOutcomeOrderAndAppliesTheAcceptedMode() {
    UUID eventId = UUID.randomUUID();
    UUID candidateId = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    var outcomes = new LinkedHashMap<UUID, SettlementResult>();
    outcomes.put(first, SettlementResult.WON);
    outcomes.put(second, SettlementResult.LOST);
    AcceptedResult accepted =
        new AcceptedResult(eventId, candidateId, MatchOutcomeMode.VOIDED, outcomes, Instant.EPOCH);

    assertThat(accepted.outcomes().keySet()).containsExactly(first, second);
    assertThat(accepted.resolve(first)).contains(SettlementResult.VOID);
    assertThat(accepted.resolve(UUID.randomUUID())).contains(SettlementResult.VOID);
  }

  @Test
  void leavesAnUnreportedCompletedSelectionUnresolved() {
    AcceptedResult accepted =
        new AcceptedResult(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MatchOutcomeMode.COMPLETED,
            new LinkedHashMap<>(),
            Instant.EPOCH);

    assertThat(accepted.resolve(UUID.randomUUID())).isEmpty();
  }
}
