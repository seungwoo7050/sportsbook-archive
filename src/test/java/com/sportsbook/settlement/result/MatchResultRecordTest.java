package com.sportsbook.settlement.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchResultRecordTest {

  @Test
  void storesAnImmutableCurrentResolutionSnapshot() {
    UUID eventId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    Map<UUID, SettlementResult> source = new HashMap<>();
    source.put(selectionId, SettlementResult.WON);
    Instant settledAt = Instant.parse("2026-08-22T00:00:00Z");
    Instant receivedAt = Instant.parse("2026-08-22T00:00:01Z");

    MatchResultRecord record =
        new MatchResultRecord(eventId, MatchOutcomeMode.COMPLETED, source, settledAt, receivedAt);
    source.put(selectionId, SettlementResult.LOST);

    assertThat(record.eventId()).isEqualTo(eventId);
    assertThat(record.mode()).isEqualTo(MatchOutcomeMode.COMPLETED);
    assertThat(record.outcomes()).containsEntry(selectionId, SettlementResult.WON);
    assertThat(record.settledAt()).isEqualTo(settledAt);
    assertThat(record.receivedAt()).isEqualTo(receivedAt);
    assertThat(record.outcomes()).isUnmodifiable();
  }
}
