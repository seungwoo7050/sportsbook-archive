package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResultCandidateFingerprinterTest {

  private final ResultCandidateFingerprinter fingerprints = new ResultCandidateFingerprinter();

  @Test
  void ignoresMapIterationOrder() {
    UUID eventId = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    Map<UUID, SettlementResult> left = new LinkedHashMap<>();
    left.put(first, SettlementResult.WON);
    left.put(second, SettlementResult.LOST);
    Map<UUID, SettlementResult> right = new LinkedHashMap<>();
    right.put(second, SettlementResult.LOST);
    right.put(first, SettlementResult.WON);

    assertThat(fingerprints.fingerprint(eventId, MatchOutcomeMode.COMPLETED, left, Instant.EPOCH))
        .isEqualTo(
            fingerprints.fingerprint(eventId, MatchOutcomeMode.COMPLETED, right, Instant.EPOCH));
  }

  @Test
  void voidedModeIgnoresSemanticallyUnusedDetail() {
    UUID eventId = UUID.randomUUID();

    assertThat(fingerprints.fingerprint(eventId, MatchOutcomeMode.VOIDED, Map.of(), Instant.EPOCH))
        .isEqualTo(
            fingerprints.fingerprint(
                eventId,
                MatchOutcomeMode.VOIDED,
                Map.of(UUID.randomUUID(), SettlementResult.WON),
                Instant.EPOCH));
  }

  @Test
  void distinguishesDifferentSourceResultTimes() {
    UUID eventId = UUID.randomUUID();

    assertThat(
            fingerprints.fingerprint(eventId, MatchOutcomeMode.COMPLETED, Map.of(), Instant.EPOCH))
        .isNotEqualTo(
            fingerprints.fingerprint(
                eventId, MatchOutcomeMode.COMPLETED, Map.of(), Instant.EPOCH.plusMillis(1)));
  }
}
