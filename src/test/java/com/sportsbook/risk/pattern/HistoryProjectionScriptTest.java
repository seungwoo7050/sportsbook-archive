package com.sportsbook.risk.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.risk.support.RedisTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.script.RedisScript;

class HistoryProjectionScriptTest extends RedisTestSupport {
  private static final List<String> KEYS =
      List.of("history:bets", "history:stakes", "history:first", "history:second");

  @Test
  void projectsEveryConfirmedFactIdempotently() {
    RedisScript<List> script = script("history-record.lua");

    assertThat(execute(script, 1000, "bet-a", "bet-a|50")).containsExactly("1", "1");
    assertThat(execute(script, 1000, "bet-a", "bet-a|50")).containsExactly("0", "0");
    assertThat(redis.opsForZSet().range(KEYS.get(0), 0, -1)).containsExactly("bet-a");
    assertThat(redis.opsForZSet().range(KEYS.get(1), 0, -1)).containsExactly("bet-a|50");
    assertThat(redis.opsForZSet().range(KEYS.get(2), 0, -1)).containsExactly("bet-a");
    assertThat(redis.opsForZSet().range(KEYS.get(3), 0, -1)).containsExactly("bet-a");
  }

  @Test
  void validatesAllKeyTypesBeforeWritingAnyFact() {
    RedisScript<List> script = script("history-record.lua");
    redis.opsForValue().set(KEYS.get(3), "wrong-type");

    assertThatThrownBy(() -> execute(script, 1000, "bet-a", "bet-a|50"))
        .isInstanceOf(RedisSystemException.class)
        .hasRootCauseMessage("history key has wrong type");
    assertThat(redis.hasKey(KEYS.get(0))).isFalse();
    assertThat(redis.hasKey(KEYS.get(1))).isFalse();
    assertThat(redis.hasKey(KEYS.get(2))).isFalse();
  }

  @Test
  void trimsHotHistoriesAndRefreshesIdleRetention() {
    RedisScript<List> script = script("history-record.lua");
    execute(script, 1, "bet-a", "bet-a|10");
    execute(script, 2, "bet-b", "bet-b|20");
    execute(script, 3, "bet-c", "bet-c|30");
    execute(script, 4, "bet-d", "bet-d|40");

    assertThat(redis.opsForZSet().range(KEYS.get(1), 0, -1))
        .containsExactly("bet-b|20", "bet-c|30", "bet-d|40");
    assertThat(redis.getExpire(KEYS.get(0))).isPositive();
    execute(script, 60002, "bet-e", "bet-e|50");
    assertThat(redis.opsForZSet().range(KEYS.get(0), 0, -1))
        .containsExactly("bet-c", "bet-d", "bet-e");
  }

  @SuppressWarnings("unchecked")
  private List<String> execute(
      RedisScript<List> script, long now, String betMember, String stakeMember) {
    return (List<String>)
        (List<?>)
            redis.execute(
                script,
                KEYS,
                Long.toString(now),
                betMember,
                stakeMember,
                "60000",
                "3",
                "86400000",
                "604800000");
  }
}
