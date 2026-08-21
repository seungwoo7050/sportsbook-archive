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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.gateway.error.GatewayProblemWriter;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.codec.ByteArrayCodec;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
    SecurityContextHolder.clearContext();
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

  @Test
  void countsFailOpenResultsWithoutDynamicTags() {
    RedisClient unavailable = mock(RedisClient.class);
    when(unavailable.connect(any(ByteArrayCodec.class)))
        .thenThrow(new RedisConnectionException("fixture outage"));
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    RateLimitProperties policies = new RateLimitProperties(true, limit, limit, List.of());
    RateLimiterService limiter = new RateLimiterService(unavailable, policies, meters);

    limiter.tryConsume("first", limit);
    limiter.tryConsume("second", limit);

    assertThat(meters.get("gateway.ratelimit.fail.open").counter().count()).isEqualTo(2);
    assertThat(meters.get("gateway.ratelimit.fail.open").counter().getId().getTags()).isEmpty();
  }

  @Test
  void returnsProblemResponseAfterSeparateIpAndUserBucketsAreExhausted() throws Exception {
    RateLimitFilter filter = filter(true);
    String peer = "198.51.100.44";
    assertThat(
            exchange(filter, request(peer)).response().getHeader(RateLimitFilter.REMAINING_HEADER))
        .isEqualTo("0");

    Exchange denied = exchange(filter, request(peer));
    assertThat(denied.response().getStatus()).isEqualTo(429);
    assertThat(denied.response().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    assertThat(denied.response().getContentAsString()).contains("GATEWAY_RATE_LIMITED");

    String user = UUID.randomUUID().toString();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(user, "unused", List.of()));
    assertThat(exchange(filter, request(peer)).response().getStatus()).isEqualTo(200);
  }

  @Test
  void disabledAndErrorDispatchesNeverConsumeTokens() throws Exception {
    Exchange disabled = exchange(filter(false), request("198.51.100.45"));
    assertThat(disabled.passed()).isTrue();
    assertThat(disabled.response().getHeader(RateLimitFilter.REMAINING_HEADER)).isNull();

    MockHttpServletRequest error = request("198.51.100.46");
    error.setDispatcherType(DispatcherType.ERROR);
    assertThat(exchange(filter(true), error).passed()).isTrue();
  }

  private RateLimitFilter filter(boolean enabled) {
    RateLimitProperties.Limit single = new RateLimitProperties.Limit(1, Duration.ofMinutes(1));
    RateLimitProperties policies = new RateLimitProperties(enabled, single, single, List.of());
    GatewayProblemWriter writer =
        new GatewayProblemWriter(
            new ObjectMapper(), new DefaultListableBeanFactory().getBeanProvider(Tracer.class));
    return new RateLimitFilter(policies, new RateLimitKeyResolver(policies), first, writer);
  }

  private static Exchange exchange(RateLimitFilter filter, MockHttpServletRequest request)
      throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean passed = new AtomicBoolean();
    filter.doFilter(request, response, (filtered, result) -> passed.set(true));
    return new Exchange(response, passed.get());
  }

  private static MockHttpServletRequest request(String peer) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(peer);
    request.setRequestURI("/api/v1/events");
    return request;
  }

  private record Exchange(MockHttpServletResponse response, boolean passed) {}

  private static RedisClient client() {
    RedisProperties properties = new RedisProperties();
    properties.setHost(REDIS.getHost());
    properties.setPort(REDIS.getMappedPort(6379));
    return new RateLimitRedisConfiguration().rateLimitRedisClient(properties);
  }
}
