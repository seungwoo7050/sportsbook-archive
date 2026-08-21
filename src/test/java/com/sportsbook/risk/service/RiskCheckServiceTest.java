package com.sportsbook.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.event.RiskSignalPublisher;
import com.sportsbook.risk.pattern.RuleEngine;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.snapshot.LimitSnapshot;
import com.sportsbook.risk.snapshot.PatternSnapshot;
import com.sportsbook.risk.snapshot.RiskSnapshot;
import com.sportsbook.risk.snapshot.RiskSnapshotReader;
import com.sportsbook.risk.snapshot.SnapshotSlot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskCheckServiceTest {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));
  private static final RiskLimitProperties POLICY =
      new RiskLimitProperties(null, null, null, null, 0);

  @Test
  void rejectsSingleBetsBeforeReadingRedis() {
    RiskSnapshotReader snapshots = mock(RiskSnapshotReader.class);
    RiskSignalPublisher signals = mock(RiskSignalPublisher.class);
    RiskCheckService service = service(snapshots, signals);

    RiskCheckOutcome outcome = service.check(command(500_001));

    assertThat(outcome.rejection().reason()).isEqualTo("SINGLE_BET_MAX_EXCEEDED");
    verifyNoInteractions(snapshots, signals);
  }

  @Test
  void evaluatesRollingLimitsInStableOrderWithCapturedOverrides() {
    RiskSnapshotReader snapshots = mock(RiskSnapshotReader.class);
    RiskSignalPublisher signals = mock(RiskSignalPublisher.class);
    EnumMap<LimitType, LimitSnapshot.Value> values = emptyValues();
    values.put(LimitType.STAKE_DAILY, new LimitSnapshot.Value(40, 10, 100L));
    values.put(LimitType.STAKE_WEEKLY, new LimitSnapshot.Value(99, 1, 100L));
    when(snapshots.read(any())).thenReturn(snapshot(values));
    RiskCheckService service = service(snapshots, signals);

    RiskCheckOutcome outcome = service.check(command(1));

    assertThat(outcome.rejection().type()).isEqualTo(LimitType.STAKE_WEEKLY);
    verify(signals)
        .publishLimit(USER, LimitType.STAKE_WEEKLY, 100, 100, Money.krw(1), Instant.EPOCH);
    verify(signals, never())
        .publishLimit(any(), eq(LimitType.STAKE_MONTHLY), anyLong(), anyLong(), any(), any());
  }

  @Test
  void appliesCurrencyNeutralSelectionCapacity() {
    RiskSnapshotReader snapshots = mock(RiskSnapshotReader.class);
    RiskSignalPublisher signals = mock(RiskSignalPublisher.class);
    EnumMap<LimitType, LimitSnapshot.Value> values = emptyValues();
    values.put(LimitType.SELECTIONS_PER_MINUTE, new LimitSnapshot.Value(20, 9, 30L));
    when(snapshots.read(any())).thenReturn(snapshot(values));
    RiskCheckService service = service(snapshots, signals);
    List<SelectionId> selections = List.of(SELECTION, SelectionId.of(new UUID(0, 4)));

    RiskCheckOutcome outcome = service.check(command(50, selections));

    assertThat(outcome.rejection().type()).isEqualTo(LimitType.SELECTIONS_PER_MINUTE);
    assertThat(outcome.rejection().currency()).isNull();
    verify(signals)
        .publishLimit(USER, LimitType.SELECTIONS_PER_MINUTE, 29, 30, Money.krw(50), Instant.EPOCH);
  }

  private static RiskCheckService service(
      RiskSnapshotReader snapshots, RiskSignalPublisher signals) {
    return new RiskCheckService(
        POLICY, snapshots, new RuleEngine(List.of()), signals, new SimpleMeterRegistry());
  }

  private static RiskCheckCommand command(long amount) {
    return command(amount, List.of(SELECTION));
  }

  private static RiskCheckCommand command(long amount, List<SelectionId> selections) {
    return new RiskCheckCommand(
        USER, BetId.of(new UUID(0, 2)), Money.krw(amount), selections, Instant.EPOCH);
  }

  private static RiskSnapshot snapshot(Map<LimitType, LimitSnapshot.Value> source) {
    EnumMap<LimitType, SnapshotSlot<LimitSnapshot.Value>> limits = new EnumMap<>(LimitType.class);
    source.forEach((type, value) -> limits.put(type, SnapshotSlot.success(value)));
    PatternSnapshot patterns =
        new PatternSnapshot(
            SnapshotSlot.success(0L),
            SnapshotSlot.success(List.of()),
            Map.of(SELECTION, SnapshotSlot.success(0L)));
    return new RiskSnapshot(new LimitSnapshot(limits), patterns);
  }

  private static EnumMap<LimitType, LimitSnapshot.Value> emptyValues() {
    EnumMap<LimitType, LimitSnapshot.Value> values = new EnumMap<>(LimitType.class);
    for (LimitType type : LimitType.values()) {
      values.put(type, new LimitSnapshot.Value(0, 0, null));
    }
    return values;
  }
}
