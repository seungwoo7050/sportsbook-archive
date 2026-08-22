package com.sportsbook.settlement.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class RevisionPlanRepositoryTest {

  @Test
  void writesTheImmutablePlanBeforeItsSelectionSnapshot() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    Instant leaseUntil = Instant.EPOCH.plusSeconds(30);
    when(jdbc.query(
            anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(List.of(Timestamp.from(leaseUntil)));
    RevisionPlan plan = plan();

    var persisted = new RevisionPlanRepository(jdbc).persist(plan, Duration.ofSeconds(30));

    var ordered = inOrder(jdbc);
    ArgumentCaptor<String> insert = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> values = ArgumentCaptor.forClass(Object[].class);
    ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<Object[]>> rows = ArgumentCaptor.forClass(List.class);
    ordered
        .verify(jdbc)
        .query(
            insert.capture(), any(org.springframework.jdbc.core.RowMapper.class), values.capture());
    ordered.verify(jdbc).batchUpdate(snapshot.capture(), rows.capture());
    assertThat(insert.getValue()).contains("from bet", "on conflict do nothing");
    assertThat(values.getValue()[11]).isEqualTo("SINGLE");
    assertThat(values.getValue()[14]).isEqualTo(100L);
    assertThat(values.getValue()[15]).isInstanceOf(Timestamp.class);
    assertThat(values.getValue()[17]).isEqualTo(30_000L);
    assertThat(snapshot.getValue()).contains("leg_index", "odds");
    assertThat(rows.getValue().get(0)).containsSequence(0, Odds.ofDecimal("2.0000").decimal());
    assertThat(persisted.created()).isTrue();
    assertThat(persisted.lease().until()).isEqualTo(leaseUntil);
  }

  private static RevisionPlan plan() {
    RevisionTarget target =
        new RevisionTarget(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            Money.krw(200),
            new BetSlipType.Single(),
            Money.krw(100),
            List.of(
                new ResolvedSelection(
                    UUID.randomUUID(), Odds.ofDecimal("2.0000"), SettlementResult.PUSH)),
            Instant.EPOCH);
    return new RevisionPlan(
        UUID.randomUUID(), target, SettlementResult.PUSH, Money.krw(100), Instant.EPOCH);
  }
}
