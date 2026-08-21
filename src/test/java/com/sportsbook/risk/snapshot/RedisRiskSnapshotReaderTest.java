package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RapidBettingPolicy;
import com.sportsbook.risk.policy.RepeatedSelectionPolicy;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.policy.SuddenStakePolicy;
import com.sportsbook.risk.reservation.RiskReservationProperties;
import com.sportsbook.risk.support.RedisTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisRiskSnapshotReaderTest extends RedisTestSupport {
  @Test
  void readsOneAtomicRedisSnapshot() {
    SelectionId selection = SelectionId.of(new UUID(0, 3));
    PatternContext context =
        new PatternContext(
            UserId.of(new UUID(0, 1)),
            BetId.of(new UUID(0, 2)),
            Money.krw(10),
            List.of(selection),
            Instant.ofEpochMilli(200_000_000));

    RiskSnapshot snapshot = reader().read(context);

    assertThat(snapshot.limits().require(LimitType.STAKE_DAILY).current()).isZero();
    assertThat(snapshot.patterns().recentBetCount().valueOrThrow()).isZero();
    assertThat(snapshot.patterns().recentStakes().valueOrThrow()).isEmpty();
    assertThat(snapshot.patterns().selectionCount(selection).valueOrThrow()).isZero();
  }

  private RedisRiskSnapshotReader reader() {
    RiskPatternProperties patterns =
        new RiskPatternProperties(
            new RapidBettingPolicy(true, Duration.ofMinutes(1), 30, PatternAction.SUSPECT),
            new SuddenStakePolicy(true, 10, 10, PatternAction.SUSPECT),
            new RepeatedSelectionPolicy(true, Duration.ofHours(24), 5, PatternAction.REVIEW));
    return new RedisRiskSnapshotReader(
        redis,
        patterns,
        new RiskReservationProperties(null, null),
        new RiskSnapshotWireMapper(new ObjectMapper()));
  }
}
