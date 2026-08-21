package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.reservation.ReservationKeys;
import com.sportsbook.risk.reservation.RiskReservationProperties;
import com.sportsbook.risk.support.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskSnapshotAggregateConsistencyScriptTest extends RedisTestSupport {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));
  private static final Instant NOW = Instant.ofEpochMilli(2_000_000);

  @Test
  void rejectsOverreportedActiveStakeWithoutPartiallyExpiring() {
    String bet = BET.value().toString();
    redis
        .opsForHash()
        .putAll(
            ReservationKeys.lifecycle(BET),
            Map.of(
                "state",
                "RESERVED",
                "userId",
                USER.value().toString(),
                "stake",
                "10",
                "currency",
                "KRW",
                "selectionCount",
                "1",
                "selections",
                SELECTION.value().toString(),
                "expiresAt",
                Long.toString(NOW.toEpochMilli())));
    redis
        .opsForZSet()
        .add(ReservationKeys.activeBets(USER), bet, NOW.minusMillis(1).toEpochMilli());
    var stakes = ReservationKeys.activeStakes(USER, Currency.KRW);
    redis.opsForZSet().add(stakes.entries(), bet + "|10", NOW.minusMillis(1).toEpochMilli());
    redis.opsForValue().set(stakes.sum(), "20");
    var selections = ReservationKeys.activeSelections(USER);
    redis.opsForZSet().add(selections.entries(), bet + "|1", NOW.minusMillis(1).toEpochMilli());
    redis.opsForValue().set(selections.sum(), "1");
    redis
        .opsForZSet()
        .add(ReservationKeys.activeSelection(USER, SELECTION), bet, NOW.toEpochMilli());
    redis.opsForValue().set(ReservationKeys.ACTIVE_COUNT, "1");

    assertThatThrownBy(() -> reader().read(context()))
        .hasStackTraceContaining("inconsistent active stake aggregate");
    assertThat(redis.opsForHash().get(ReservationKeys.lifecycle(BET), "state"))
        .isEqualTo("RESERVED");
    assertThat(redis.opsForZSet().size(ReservationKeys.activeBets(USER))).isEqualTo(1);
    assertThat(redis.opsForValue().get(stakes.sum())).isEqualTo("20");
  }

  private RedisRiskSnapshotReader reader() {
    return new RedisRiskSnapshotReader(
        redis,
        new RiskPatternProperties(null, null, null),
        new RiskReservationProperties(null, null),
        new RiskSnapshotWireMapper(new ObjectMapper()));
  }

  private static PatternContext context() {
    return new PatternContext(USER, BET, new Money(1, Currency.KRW), List.of(SELECTION), NOW);
  }
}
