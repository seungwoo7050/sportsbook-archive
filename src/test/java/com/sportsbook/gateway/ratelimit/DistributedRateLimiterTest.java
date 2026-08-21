package com.sportsbook.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.lettuce.core.RedisClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class DistributedRateLimiterTest {

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private final RateLimitProperties.Limit limit =
      new RateLimitProperties.Limit(2, Duration.ofSeconds(2));
  private RedisClient firstClient;
  private RedisClient secondClient;
  private RateLimiterService first;
  private RateLimiterService second;

  @BeforeEach
  void createLimiters() {
    RateLimitProperties policies = new RateLimitProperties(true, limit, limit, List.of());
    firstClient = client();
    secondClient = client();
    first = new RateLimiterService(firstClient, policies);
    second = new RateLimiterService(secondClient, policies);
  }

  @AfterEach
  void closeLimiters() {
    first.closeConnection();
    second.closeConnection();
    firstClient.shutdown();
    secondClient.shutdown();
  }

  @Test
  void sharesCapacityAcrossGatewayInstancesAndRefills() {
    String key = "gateway:ratelimit:ip:" + UUID.randomUUID();

    RateLimiterService.Result firstUse = first.tryConsume(key, limit);
    RateLimiterService.Result secondUse = second.tryConsume(key, limit);
    RateLimiterService.Result exhausted = first.tryConsume(key, limit);

    assertThat(firstUse.allowed()).isTrue();
    assertThat(secondUse.allowed()).isTrue();
    assertThat(exhausted.allowed()).isFalse();
    assertThat(exhausted.retryAfterSeconds()).isPositive();

    await()
        .atMost(Duration.ofSeconds(2))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> assertThat(second.tryConsume(key, limit).allowed()).isTrue());
  }

  private static RedisClient client() {
    RedisProperties properties = new RedisProperties();
    properties.setHost(REDIS.getHost());
    properties.setPort(REDIS.getMappedPort(6379));
    return new RateLimitRedisConfiguration().rateLimitRedisClient(properties);
  }
}
