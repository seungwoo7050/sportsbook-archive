package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.BetId;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.protocol.value.UserId;
import com.sportsbook.risk.counter.LimitKeys;
import com.sportsbook.risk.counter.LimitType;
import com.sportsbook.risk.limit.LimitOverrideField;
import com.sportsbook.risk.limit.LimitOverrideKeys;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.reservation.RiskReservationProperties;
import com.sportsbook.risk.support.RedisTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class RiskSnapshotScriptTest extends RedisTestSupport {
  private static final UserId USER = UserId.of(new UUID(0, 1));
  private static final BetId BET = BetId.of(new UUID(0, 2));
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 3));
  private static final Instant NOW = Instant.ofEpochMilli(200_000_000);

  @Test
  void readsCommittedCountersAndCapturedOverrides() throws Exception {
    LimitKeys.Keys daily = LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Currency.KRW);
    redis.opsForZSet().add(daily.entries(), LimitKeys.member(BET, 100), NOW.toEpochMilli() - 1);
    redis.opsForValue().set(daily.sum(), "100");
    redis
        .opsForHash()
        .put(
            LimitOverrideKeys.user(USER),
            LimitOverrideField.monetary(LimitType.STAKE_DAILY, Currency.KRW).redisField(),
            "200");

    JsonNode result = execute();

    JsonNode slot = result.path("limits").path("STAKE_DAILY");
    assertThat(slot.path("ok").asBoolean()).isTrue();
    assertThat(slot.path("committed").asText()).isEqualTo("100");
    assertThat(slot.path("active").asText()).isEqualTo("0");
    assertThat(slot.path("override").asText()).isEqualTo("200");
    assertThat(result.path("limits").path("STAKE_WEEKLY").path("committed").asText())
        .isEqualTo("0");
  }

  @Test
  void expiresCommittedWindowsAndRepairsOrphanSums() throws Exception {
    LimitKeys.Keys daily = LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Currency.KRW);
    long expired = NOW.minus(LimitType.STAKE_DAILY.window()).minusMillis(1).toEpochMilli();
    redis.opsForZSet().add(daily.entries(), LimitKeys.member(BET, 100), expired);
    redis.opsForValue().set(daily.sum(), "100");

    assertThat(execute().path("limits").path("STAKE_DAILY").path("committed").asText())
        .isEqualTo("0");
    assertThat(redis.hasKey(daily.entries())).isFalse();
    assertThat(redis.hasKey(daily.sum())).isFalse();
  }

  @Test
  void defersCorruptCounterFailuresInTheirSlots() throws Exception {
    LimitKeys.Keys daily = LimitKeys.monetary(USER, LimitType.STAKE_DAILY, Currency.KRW);
    redis.opsForZSet().add(daily.entries(), LimitKeys.member(BET, 100), NOW.toEpochMilli());

    JsonNode slot = execute().path("limits").path("STAKE_DAILY");

    assertThat(slot.path("ok").asBoolean()).isFalse();
    assertThat(slot.path("error").asText()).contains("sum");
  }

  private JsonNode execute() throws Exception {
    PatternContext context = new PatternContext(USER, BET, Money.krw(1), List.of(SELECTION), NOW);
    RiskSnapshotScriptRequest request =
        RiskSnapshotScriptRequest.from(
            context,
            new RiskPatternProperties(null, null, null),
            new RiskReservationProperties(null, null));
    DefaultRedisScript<String> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("scripts/risk-snapshot.lua"));
    script.setResultType(String.class);
    String raw = redis.execute(script, request.keys(), request.arguments().toArray());
    return new ObjectMapper().readTree(raw);
  }
}
