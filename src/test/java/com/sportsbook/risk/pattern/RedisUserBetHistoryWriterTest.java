package com.sportsbook.risk.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.support.RedisTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisUserBetHistoryWriterTest extends RedisTestSupport {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));

  @Test
  void isolatesStakeCurrenciesWhileSharingGlobalFacts() {
    RedisUserBetHistoryWriter writer = writer();
    PatternContext krw = context(2, Money.krw(600));
    PatternContext usd = context(4, Money.usd(20));

    assertThat(writer.record(krw)).isEqualTo(new UserBetHistoryWriter.WriteResult(true, true));
    assertThat(writer.record(usd)).isEqualTo(new UserBetHistoryWriter.WriteResult(true, true));
    assertThat(writer.record(krw)).isEqualTo(new UserBetHistoryWriter.WriteResult(false, false));

    assertThat(redis.opsForZSet().range(HistoryKeys.stakes(USER, krw.stake().currency()), 0, -1))
        .containsExactly(HistoryKeys.stakeMember(krw.betId(), 600));
    assertThat(redis.opsForZSet().range(HistoryKeys.stakes(USER, usd.stake().currency()), 0, -1))
        .containsExactly(HistoryKeys.stakeMember(usd.betId(), 20));
    assertThat(redis.opsForZSet().size(HistoryKeys.bets(USER))).isEqualTo(2);
    assertThat(redis.opsForZSet().size(HistoryKeys.selection(USER, SELECTION))).isEqualTo(2);
    assertThat(redis.getExpire(HistoryKeys.bets(USER))).isPositive();
  }

  private RedisUserBetHistoryWriter writer() {
    return new RedisUserBetHistoryWriter(
        redis,
        new RiskPatternProperties(null, null, null),
        new RiskHistoryProperties(Duration.ofDays(7), 5));
  }

  private static PatternContext context(long betSequence, Money stake) {
    return new PatternContext(
        USER,
        BetId.of(new UUID(0, betSequence)),
        stake,
        List.of(SELECTION),
        Instant.ofEpochMilli(betSequence));
  }
}
