package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BetPlacedKafkaRedisIntegrationTest extends BetPlacedKafkaRedisIntegrationSupport {
  private static final String BET_ID = "20000000-0000-4000-8000-000000000001";
  private static final String DAILY_BASE =
      "risk:limit:{" + BetPlacedEventFixture.USER_ID + "}:stake-daily:krw";

  @Test
  void acceptedBetProjectsOnceAcrossBrokerRedelivery() throws Exception {
    publishAcceptedBet();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(redis.opsForValue().get(DAILY_BASE + ":sum")).isEqualTo("10000");
              assertThat(redis.opsForValue().get("risk:event:fingerprint:" + BET_ID))
                  .matches("[0-9a-f]{64}");
              assertThat(committedSourceOffset()).isEqualTo(1L);
            });

    publishAcceptedBet();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(committedSourceOffset()).isEqualTo(2L));
    assertThat(redis.opsForValue().get(DAILY_BASE + ":sum")).isEqualTo("10000");
    assertThat(redis.opsForZSet().size(DAILY_BASE + ":entries")).isEqualTo(1L);
  }
}
