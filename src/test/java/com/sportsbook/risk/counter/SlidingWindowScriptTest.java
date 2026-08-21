package com.sportsbook.risk.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.risk.support.RedisTestSupport;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.script.RedisScript;

class SlidingWindowScriptTest extends RedisTestSupport {
  private static final List<String> KEYS = List.of("test:entries", "test:sum");

  @Test
  void recordsOnceAndExpiresEntriesAtomically() {
    RedisScript<List> script = script("sliding-window.lua");

    assertThat(execute(script, "RECORD", 1000, 1000, "bet-a|100", 100)).containsExactly("100", "1");
    assertThat(execute(script, "RECORD", 1200, 1000, "bet-a|100", 100)).containsExactly("100", "0");
    assertThat(execute(script, "RECORD", 1500, 1000, "bet-b|50", 50)).containsExactly("150", "1");
    assertThat(execute(script, "READ", 2001, 1000, "", 0)).containsExactly("50", "0");
    assertThat(execute(script, "READ", 2501, 1000, "", 0)).containsExactly("0", "0");
    assertThat(redis.hasKey(KEYS.get(0))).isFalse();
  }

  @Test
  void repairsOrphanSumsAndRejectsCorruptMembers() {
    RedisScript<List> script = script("sliding-window.lua");
    redis.opsForValue().set(KEYS.get(1), "99");

    assertThat(execute(script, "READ", 1000, 1000, "", 0)).containsExactly("0", "0");
    redis.opsForZSet().add(KEYS.get(0), "invalid", 1);
    redis.opsForValue().set(KEYS.get(1), "1");

    assertThatThrownBy(() -> execute(script, "READ", 2000, 1000, "", 0))
        .isInstanceOf(RedisSystemException.class)
        .hasRootCauseMessage("corrupt sliding-window member");
  }

  @Test
  void convergesUnderConcurrentSameTimestampWrites() {
    RedisScript<List> script = script("sliding-window.lua");
    var executor = Executors.newFixedThreadPool(8);
    try {
      List<CompletableFuture<List<String>>> writes =
          IntStream.range(0, 20)
              .mapToObj(
                  index ->
                      CompletableFuture.supplyAsync(
                          () -> execute(script, "RECORD", 1000, 1000, "bet-" + index + "|1", 1),
                          executor))
              .toList();
      CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();

      assertThat(writes).allSatisfy(write -> assertThat(write.join().get(1)).isEqualTo("1"));
      assertThat(execute(script, "READ", 1000, 1000, "", 0)).containsExactly("20", "0");
      assertThat(redis.opsForZSet().size(KEYS.get(0))).isEqualTo(20);
    } finally {
      executor.shutdownNow();
    }
  }

  @SuppressWarnings("unchecked")
  private List<String> execute(
      RedisScript<List> script, String mode, long now, long window, String member, long amount) {
    return (List<String>)
        (List<?>)
            redis.execute(
                script,
                KEYS,
                mode,
                Long.toString(now),
                Long.toString(window),
                "5000",
                member,
                Long.toString(amount));
  }
}
