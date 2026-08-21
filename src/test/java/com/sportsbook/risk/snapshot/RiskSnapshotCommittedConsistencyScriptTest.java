package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitKeys;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.reservation.RiskReservationProperties;
import com.sportsbook.risk.support.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskSnapshotCommittedConsistencyScriptTest extends RedisTestSupport {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final Instant NOW = Instant.ofEpochMilli(2_000_000);

  @Test
  void reportsAnOverstatedCommittedSumWithoutMutatingTheWindow() {
    LimitKeys.Keys daily = LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Currency.KRW);
    String member = LimitKeys.member(BET, 10);
    redis.opsForZSet().add(daily.entries(), member, NOW.minusMillis(1).toEpochMilli());
    redis.opsForValue().set(daily.sum(), "20");

    RiskSnapshot snapshot = reader().read(context());

    assertThat(snapshot.limits().values().get(LimitType.STAKE_DAILY).failure())
        .contains("inconsistent rolling sum");
    assertThat(redis.opsForZSet().range(daily.entries(), 0, -1)).containsExactly(member);
    assertThat(redis.opsForValue().get(daily.sum())).isEqualTo("20");
  }

  private RedisRiskSnapshotReader reader() {
    return new RedisRiskSnapshotReader(
        redis,
        new RiskPatternProperties(null, null, null),
        new RiskReservationProperties(null, null),
        new RiskSnapshotWireMapper(new ObjectMapper()));
  }

  private static PatternContext context() {
    return new PatternContext(
        USER,
        BetId.of(new UUID(0, 9)),
        new Money(1, Currency.KRW),
        List.of(SelectionId.of(new UUID(0, 3))),
        NOW);
  }
}
