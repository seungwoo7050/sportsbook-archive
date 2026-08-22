package com.sportsbook.settlement.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.SettlementResult;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "settlement.wallet.api-key=0123456789abcdef0123456789abcdef"
    })
class MatchResultRepositoryTest {

  @Autowired private MatchResultRepository repository;

  @Test
  void retrievesTheMaterializedSelectionResolution() {
    UUID eventId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    repository.saveAndFlush(
        new MatchResultRecord(
            eventId,
            MatchOutcomeMode.ABANDONED,
            Map.of(selectionId, SettlementResult.PUSH),
            Instant.parse("2026-08-22T00:00:00Z"),
            Instant.parse("2026-08-22T00:00:01Z")));

    MatchResultRecord found = repository.findById(eventId).orElseThrow();

    assertThat(found.mode()).isEqualTo(MatchOutcomeMode.ABANDONED);
    assertThat(found.outcomes()).containsEntry(selectionId, SettlementResult.PUSH);
  }
}
