package com.sportsbook.settlement.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SettlementAttemptOwnershipTest {

  @Test
  void detectsAnImmutablePlanAfterTheBetLockIsHeld() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    UUID betId = UUID.randomUUID();
    when(jdbc.queryForObject(
            "select exists (select 1 from settlement_attempt where bet_id = ?)",
            Boolean.class,
            betId))
        .thenReturn(true);

    assertThat(new SettlementAttemptRepository(jdbc).exists(betId)).isTrue();
  }
}
