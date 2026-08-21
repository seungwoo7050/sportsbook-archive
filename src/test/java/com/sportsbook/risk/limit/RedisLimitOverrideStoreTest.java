package com.sportsbook.risk.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.policy.SafeRedisNumber;
import com.sportsbook.risk.support.RedisTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RedisLimitOverrideStoreTest extends RedisTestSupport {
  private static final UserId USER =
      UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));

  @Test
  void roundTripsIsolatedOverrideDimensions() {
    RedisLimitOverrideStore store = new RedisLimitOverrideStore(redis);
    LimitOverrideField krw = LimitOverrideField.monetary(LimitType.STAKE_DAILY, Currency.KRW);
    LimitOverrideField usd = LimitOverrideField.monetary(LimitType.STAKE_DAILY, Currency.USD);
    LimitOverrideField selections = LimitOverrideField.selections();

    assertThat(store.find(USER, krw)).isEmpty();
    store.set(USER, krw, 1000);
    store.set(USER, usd, 10);
    store.set(USER, selections, 7);

    assertThat(store.find(USER, krw)).hasValue(1000);
    assertThat(store.find(USER, usd)).hasValue(10);
    assertThat(store.find(USER, selections)).hasValue(7);
    store.clear(USER, krw);
    assertThat(store.find(USER, krw)).isEmpty();
    assertThat(store.find(USER, usd)).hasValue(10);
  }

  @Test
  void failsClosedForUnsafeWritesAndCorruptStoredValues() {
    RedisLimitOverrideStore store = new RedisLimitOverrideStore(redis);
    LimitOverrideField field = LimitOverrideField.monetary(LimitType.STAKE_MONTHLY, Currency.KRW);

    assertThatThrownBy(() -> store.set(USER, field, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.set(USER, field, SafeRedisNumber.MAX_VALUE + 1))
        .isInstanceOf(IllegalArgumentException.class);
    redis.opsForHash().put(RedisLimitOverrideStore.key(USER), field.redisField(), "corrupt");
    assertThatThrownBy(() -> store.find(USER, field))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("stored override is not an integer");
    redis.opsForHash().put(RedisLimitOverrideStore.key(USER), field.redisField(), "-1");
    assertThatThrownBy(() -> store.find(USER, field)).isInstanceOf(IllegalArgumentException.class);
  }
}
