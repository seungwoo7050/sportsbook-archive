package com.sportsbook.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.codec.ByteArrayCodec;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
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

  @Test
  void warmedConnectionFailsOpenWithinTheBoundAndRecovers() {
    String key = "gateway:ratelimit:ip:" + UUID.randomUUID();
    assertThat(first.tryConsume(key, limit).failOpen()).isFalse();

    REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
    try {
      RateLimiterService.Result degraded =
          assertTimeoutPreemptively(Duration.ofSeconds(2), () -> first.tryConsume(key, limit));
      assertThat(degraded.allowed()).isTrue();
      assertThat(degraded.failOpen()).isTrue();
      assertThat(degraded.remainingTokens()).isEqualTo(-1);
    } finally {
      REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
    }

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(first.tryConsume(key, limit).failOpen()).isFalse());
  }

  @Test
  void coldConnectionAttemptIsSingleFlightAndRecovers() throws Exception {
    RedisClient delegate = client();
    RedisClient blocked = mock(RedisClient.class);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger attempts = new AtomicInteger();
    when(blocked.connect(any(ByteArrayCodec.class)))
        .thenAnswer(
            invocation -> {
              if (attempts.incrementAndGet() == 1) {
                entered.countDown();
                release.await();
                throw new RedisConnectionException("fixture outage");
              }
              ByteArrayCodec codec = invocation.getArgument(0);
              return delegate.connect(codec);
            });
    RateLimitProperties policies = new RateLimitProperties(true, limit, limit, List.of());
    RateLimiterService limiter = new RateLimiterService(blocked, policies);
    ExecutorService workers = Executors.newFixedThreadPool(5);
    String key = "gateway:ratelimit:ip:" + UUID.randomUUID();
    try {
      Future<RateLimiterService.Result> leader =
          workers.submit(() -> limiter.tryConsume(key, limit));
      assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
      List<Future<RateLimiterService.Result>> followers =
          IntStream.range(0, 4)
              .mapToObj(ignored -> workers.submit(() -> limiter.tryConsume(key, limit)))
              .toList();
      for (Future<RateLimiterService.Result> follower : followers) {
        assertThat(follower.get(1, TimeUnit.SECONDS))
            .isEqualTo(new RateLimiterService.Result(true, -1, 0, true));
      }
      verify(blocked, times(1)).connect(any(ByteArrayCodec.class));
      release.countDown();
      assertThat(leader.get(1, TimeUnit.SECONDS))
          .isEqualTo(new RateLimiterService.Result(true, -1, 0, true));
      assertThat(limiter.tryConsume(key + ":recovered", limit).failOpen()).isFalse();
      verify(blocked, times(2)).connect(any(ByteArrayCodec.class));
    } finally {
      release.countDown();
      workers.shutdownNow();
      limiter.closeConnection();
      delegate.shutdown();
    }
  }

  @Test
  void invalidLimitIsNeverConvertedToFailOpen() {
    RateLimitProperties.Limit invalid = new RateLimitProperties.Limit(0, Duration.ofSeconds(1));
    assertThatThrownBy(() -> first.tryConsume("invalid", invalid))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static RedisClient client() {
    RedisProperties properties = new RedisProperties();
    properties.setHost(REDIS.getHost());
    properties.setPort(REDIS.getMappedPort(6379));
    return new RateLimitRedisConfiguration().rateLimitRedisClient(properties);
  }
}
