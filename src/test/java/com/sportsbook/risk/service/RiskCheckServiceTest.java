package com.sportsbook.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.event.RiskSignalPublisher;
import com.sportsbook.risk.pattern.RuleEngine;
import com.sportsbook.risk.policy.RiskLimitProperties;
import com.sportsbook.risk.snapshot.RiskSnapshotReader;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
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

  private static RiskCheckService service(
      RiskSnapshotReader snapshots, RiskSignalPublisher signals) {
    return new RiskCheckService(
        POLICY, snapshots, new RuleEngine(List.of()), signals, new SimpleMeterRegistry());
  }

  private static RiskCheckCommand command(long amount) {
    return new RiskCheckCommand(
        USER, BetId.of(new UUID(0, 2)), Money.krw(amount), List.of(SELECTION), Instant.EPOCH);
  }
}
