package com.sportsbook.gateway.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public final class RateLimiterService {

  private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
  private final RedisClient redisClient;
  private final Duration expirationGrace;
  private final Counter failOpenCounter;
  private final ReentrantLock managerLock = new ReentrantLock();
  private volatile StatefulRedisConnection<byte[], byte[]> connection;
  private volatile ProxyManager<byte[]> manager;

  @Autowired
  public RateLimiterService(
      RedisClient redisClient, RateLimitProperties properties, MeterRegistry meterRegistry) {
    this.redisClient = redisClient;
    this.failOpenCounter = meterRegistry.counter("gateway.ratelimit.fail.open");
    this.expirationGrace =
        properties.user().refillPeriod().compareTo(properties.ip().refillPeriod()) >= 0
            ? properties.user().refillPeriod()
            : properties.ip().refillPeriod();
  }

  public RateLimiterService(RedisClient redisClient, RateLimitProperties properties) {
    this(redisClient, properties, Metrics.globalRegistry);
  }

  public Result tryConsume(String key, RateLimitProperties.Limit limit) {
    BucketConfiguration configuration =
        BucketConfiguration.builder()
            .addLimit(
                bandwidth ->
                    bandwidth
                        .capacity(limit.capacity())
                        .refillGreedy(limit.capacity(), limit.refillPeriod()))
            .build();
    try {
      BucketProxy bucket =
          manager().builder().build(key.getBytes(StandardCharsets.UTF_8), configuration);
      ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
      return probe.isConsumed()
          ? new Result(true, probe.getRemainingTokens(), 0, false)
          : new Result(false, 0, waitSeconds(probe.getNanosToWaitForRefill()), false);
    } catch (RedisException failure) {
      failOpenCounter.increment();
      log.warn("Rate limiter fail-open: {}", failure.getClass().getSimpleName());
      return new Result(true, -1, 0, true);
    }
  }

  private static long waitSeconds(long nanos) {
    return Math.max(1, 1 + (nanos - 1) / 1_000_000_000L);
  }

  private ProxyManager<byte[]> manager() {
    if (manager != null) {
      return manager;
    }
    if (!managerLock.tryLock()) {
      throw new RedisException("Rate limiter initialization is in progress");
    }
    try {
      if (manager == null) {
        StatefulRedisConnection<byte[], byte[]> opened =
            redisClient.connect(ByteArrayCodec.INSTANCE);
        ProxyManager<byte[]> created =
            LettuceBasedProxyManager.builderFor(opened)
                .withExpirationStrategy(
                    ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        expirationGrace))
                .build();
        connection = opened;
        manager = created;
      }
      return manager;
    } finally {
      managerLock.unlock();
    }
  }

  @PreDestroy
  void closeConnection() {
    if (connection != null) {
      connection.close();
    }
  }

  public record Result(
      boolean allowed, long remainingTokens, long retryAfterSeconds, boolean failOpen) {}
}
