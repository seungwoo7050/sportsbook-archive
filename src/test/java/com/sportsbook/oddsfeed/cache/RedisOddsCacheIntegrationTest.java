package com.sportsbook.oddsfeed.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisOddsCacheIntegrationTest {

  @Container
  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  private LettuceConnectionFactory connectionFactory;
  private StringRedisTemplate redis;

  @BeforeEach
  void setUp() {
    connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
    connectionFactory.afterPropertiesSet();
    redis = new StringRedisTemplate(connectionFactory);
    redis.afterPropertiesSet();
  }

  @AfterEach
  void tearDown() {
    connectionFactory.destroy();
  }

  @Test
  void projectsProviderStatusWithAtomicRegistryKeys() {
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    RedisOddsCache cache = cache();

    assertThat(cache.storeProviderMarketStatus(eventId, marketId, MarketStatus.SUSPENDED))
        .isEqualTo(MarketStatus.SUSPENDED);

    assertThat(cache.getMarketStatus(eventId, marketId)).contains(MarketStatus.SUSPENDED);
    assertThat(redis.opsForValue().get(CacheKeys.providerMarket(eventId, marketId)))
        .isEqualTo(MarketStatus.SUSPENDED.name());
    assertThat(cache.getRegisteredMarkets(eventId)).containsEntry(marketId, MarketStatus.SUSPENDED);
  }

  private RedisOddsCache cache() {
    return new RedisOddsCache(
        redis,
        new ObjectMapper().findAndRegisterModules(),
        new CacheProperties(Duration.ofHours(24)));
  }
}
