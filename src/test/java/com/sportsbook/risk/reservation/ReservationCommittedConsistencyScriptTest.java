package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.risk.counter.LimitKeys;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.service.RiskCheckCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationCommittedConsistencyScriptTest extends ReservationScriptTestSupport {
  @Test
  void preservesCorruptCommittedWindowAndRejectsAdmission() {
    Instant now = Instant.ofEpochMilli(3_000_000_000L);
    LimitKeys.Keys daily = LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Currency.KRW);
    String expired = LimitKeys.member(BetId.of(new UUID(0, 900)), 30);
    String live = LimitKeys.member(BetId.of(new UUID(0, 901)), 20);
    redis
        .opsForZSet()
        .add(
            daily.entries(),
            expired,
            now.minus(LimitType.STAKE_DAILY.window()).minusMillis(1).toEpochMilli());
    redis.opsForZSet().add(daily.entries(), live, now.minusMillis(1).toEpochMilli());
    redis.opsForValue().set(daily.sum(), "51");
    RiskCheckCommand candidate =
        new RiskCheckCommand(
            USER,
            BetId.of(new UUID(0, 902)),
            new Money(10, Currency.KRW),
            List.of(selection(902)),
            now);

    assertThatThrownBy(() -> execute(request(candidate)))
        .hasStackTraceContaining("corrupt rolling aggregate");

    assertThat(redis.hasKey(ReservationKeys.lifecycle(candidate.betId()))).isFalse();
    assertThat(redis.opsForValue().get(daily.sum())).isEqualTo("51");
    assertThat(redis.opsForZSet().range(daily.entries(), 0, -1)).containsExactly(expired, live);
  }
}
