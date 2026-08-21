package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.risk.counter.RedisLuaScriptLoader;
import com.sportsbook.risk.support.RedisTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.script.RedisScript;

class AcceptedProjectionIdentityScriptTest extends RedisTestSupport {
  private static final RedisScript<String> SCRIPT =
      RedisLuaScriptLoader.stringScript("risk-project-accepted.lua");
  private static final String FINGERPRINT = "a".repeat(64);

  @Test
  void replaysOnlyTheRetainedFingerprint() {
    redis.opsForValue().set("accepted", FINGERPRINT);

    assertThat(execute(FINGERPRINT)).isEqualTo("REPLAYED");
    assertThat(execute("b".repeat(64))).isEqualTo("CONFLICT");
  }

  @Test
  void rejectsAnExistingReservationLifecycle() {
    redis.opsForHash().put("lifecycle", "state", "RESERVED");

    assertThatThrownBy(() -> execute(FINGERPRINT))
        .isInstanceOf(RedisSystemException.class)
        .rootCause()
        .hasMessageContaining("reservation lifecycle appeared");
    assertThat(redis.hasKey("accepted")).isFalse();
  }

  private String execute(String fingerprint) {
    return redis.execute(SCRIPT, List.of("lifecycle", "accepted"), arguments(fingerprint));
  }

  private static Object[] arguments(String fingerprint) {
    return new Object[] {
      "1",
      "2000000",
      "2764800000",
      fingerprint,
      "00000000-0000-0000-0000-000000000001",
      "00000000-0000-0000-0000-000000000010",
      "50",
      "KRW",
      "1",
      "00000000-0000-0000-0000-000000000020",
      "86400000",
      "604800000",
      "2592000000",
      "60000",
      "60000",
      "86400000",
      "600000",
      "100"
    };
  }
}
