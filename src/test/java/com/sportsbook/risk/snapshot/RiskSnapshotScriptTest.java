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
import com.sportsbook.risk.pattern.HistoryKeys;
import com.sportsbook.risk.pattern.PatternContext;
import com.sportsbook.risk.policy.PatternAction;
import com.sportsbook.risk.policy.RapidBettingPolicy;
import com.sportsbook.risk.policy.RepeatedSelectionPolicy;
import com.sportsbook.risk.policy.RiskPatternProperties;
import com.sportsbook.risk.policy.SuddenStakePolicy;
import com.sportsbook.risk.reservation.ReservationKeys;
import com.sportsbook.risk.reservation.RiskReservationProperties;
import com.sportsbook.risk.support.RedisTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

  @Test
  void readsGlobalAndCurrencyScopedConfirmedPatternFacts() throws Exception {
    redis
        .opsForZSet()
        .add(HistoryKeys.bets(USER), HistoryKeys.betMember(BET), NOW.toEpochMilli() - 1);
    redis
        .opsForZSet()
        .add(
            HistoryKeys.stakes(USER, Currency.KRW),
            HistoryKeys.stakeMember(BET, 100),
            NOW.toEpochMilli() - 1);
    redis
        .opsForZSet()
        .add(
            HistoryKeys.selection(USER, SELECTION),
            HistoryKeys.betMember(BET),
            NOW.toEpochMilli() - 1);

    JsonNode patterns = execute().path("patterns");

    assertThat(patterns.path("rapid").path("value").asText()).isEqualTo("1");
    assertThat(patterns.path("stakes").path("value").asText()).isEqualTo("100");
    assertThat(patterns.path("selections").get(0).path("slot").path("value").asText())
        .isEqualTo("1");
  }

  @Test
  void includesLiveCapacityInLimitsAndPatternFacts() throws Exception {
    seedActive(NOW.plusSeconds(30));

    JsonNode result = execute();

    assertThat(result.path("limits").path("STAKE_DAILY").path("active").asText()).isEqualTo("50");
    assertThat(result.path("limits").path("SELECTIONS_PER_MINUTE").path("active").asText())
        .isEqualTo("1");
    assertThat(result.path("patterns").path("rapid").path("value").asText()).isEqualTo("1");
    assertThat(result.path("patterns").path("stakes").path("value").asText()).isEqualTo("50");
  }

  @Test
  void expiresEveryReservationFootprintBeforeReadingCapacity() throws Exception {
    seedActive(NOW.minusMillis(1));

    JsonNode result = execute();

    assertThat(result.path("expired").asText()).isEqualTo("1");
    assertThat(result.path("limits").path("STAKE_DAILY").path("active").asText()).isEqualTo("0");
    assertThat(redis.opsForHash().get(ReservationKeys.lifecycle(BET), "state"))
        .isEqualTo("EXPIRED");
    assertThat(redis.hasKey(ReservationKeys.activeBets(USER))).isFalse();
    assertThat(redis.hasKey(ReservationKeys.activeSelection(USER, SELECTION))).isFalse();
    assertThat(redis.opsForValue().get(ReservationKeys.ACTIVE_COUNT)).isNull();
  }

  private void seedActive(Instant expiresAt) {
    String bet = BET.value().toString();
    long score = NOW.toEpochMilli() - 1;
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
                "50",
                "currency",
                "KRW",
                "selectionCount",
                "1",
                "selections",
                SELECTION.value().toString(),
                "expiresAt",
                Long.toString(expiresAt.toEpochMilli())));
    redis.opsForZSet().add(ReservationKeys.activeBets(USER), bet, score);
    LimitKeys.Keys activeStakes = ReservationKeys.activeStakes(USER, Currency.KRW);
    redis.opsForZSet().add(activeStakes.entries(), bet + "|50", score);
    redis.opsForValue().set(activeStakes.sum(), "50");
    LimitKeys.Keys activeSelections = ReservationKeys.activeSelections(USER);
    redis.opsForZSet().add(activeSelections.entries(), bet + "|1", score);
    redis.opsForValue().set(activeSelections.sum(), "1");
    redis.opsForZSet().add(ReservationKeys.activeSelection(USER, SELECTION), bet, score);
    redis.opsForValue().set(ReservationKeys.ACTIVE_COUNT, "1");
  }

  private JsonNode execute() throws Exception {
    PatternContext context = new PatternContext(USER, BET, Money.krw(1), List.of(SELECTION), NOW);
    RiskPatternProperties patterns =
        new RiskPatternProperties(
            new RapidBettingPolicy(true, Duration.ofMinutes(1), 30, PatternAction.SUSPECT),
            new SuddenStakePolicy(true, 10, 10, PatternAction.SUSPECT),
            new RepeatedSelectionPolicy(true, Duration.ofHours(24), 5, PatternAction.REVIEW));
    RiskSnapshotScriptRequest request =
        RiskSnapshotScriptRequest.from(
            context, patterns, new RiskReservationProperties(null, null));
    DefaultRedisScript<String> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("scripts/risk-snapshot.lua"));
    script.setResultType(String.class);
    String raw = redis.execute(script, request.keys(), request.arguments().toArray());
    return new ObjectMapper().readTree(raw);
  }
}
