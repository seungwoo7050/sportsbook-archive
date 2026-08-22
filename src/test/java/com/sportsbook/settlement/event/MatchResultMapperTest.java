package com.sportsbook.settlement.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.MatchFinalStatus;
import com.sportsbook.protocol.event.MatchResult;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import com.sportsbook.settlement.result.MatchResultRecord;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchResultMapperTest {

  private final MatchResultMapper mapper = new MatchResultMapper();

  @Test
  void mapsCompletedSelectionOutcomesWithoutScoreInference() {
    UUID selectionId = UUID.randomUUID();
    MatchResult event = event(MatchFinalStatus.COMPLETED, Map.of(selectionId.toString(), "WON"));

    MatchResultRecord result = mapper.map(event, Instant.parse("2026-08-22T00:00:01Z"));

    assertThat(result.mode()).isEqualTo(MatchOutcomeMode.COMPLETED);
    assertThat(result.outcomes()).containsEntry(selectionId, SettlementResult.WON);
  }

  @Test
  void keepsVoidedFinalStatusOnNormalResultResolutionPath() {
    UUID selectionId = UUID.randomUUID();
    MatchResult event = event(MatchFinalStatus.VOIDED, Map.of(selectionId.toString(), "WON"));

    MatchResultRecord result = mapper.map(event, Instant.EPOCH.plusSeconds(1));

    assertThat(result.mode()).isEqualTo(MatchOutcomeMode.VOIDED);
    assertThat(result.mode().resolve(result.outcomes().get(selectionId)))
        .contains(SettlementResult.VOID);
  }

  @Test
  void rejectsNoncanonicalIdentifiersAndUnknownOutcomes() {
    UUID selectionId = UUID.randomUUID();
    MatchResult uppercase =
        event(
            MatchFinalStatus.COMPLETED,
            Map.of(selectionId.toString().toUpperCase(java.util.Locale.ROOT), "WON"));
    MatchResult unknown =
        event(MatchFinalStatus.COMPLETED, Map.of(selectionId.toString(), "CANCELLED"));

    assertThatThrownBy(() -> mapper.map(uppercase, Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> mapper.map(unknown, Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static MatchResult event(MatchFinalStatus status, Map<String, String> outcomes) {
    return MatchResult.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setScore("ignored")
        .setFinalStatus(status)
        .setResultDetail(outcomes)
        .setSettledAt(Instant.EPOCH)
        .build();
  }
}
