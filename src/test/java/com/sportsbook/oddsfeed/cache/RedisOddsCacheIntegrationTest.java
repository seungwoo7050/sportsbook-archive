package com.sportsbook.oddsfeed.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.oddsfeed.config.CacheProperties;
import com.sportsbook.protocol.event.MarketStatus;
import com.sportsbook.protocol.value.EventId;
import com.sportsbook.protocol.value.MarketId;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.protocol.value.SelectionId;
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

  @Test
  void registrySurvivesCacheRestart() {
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    RedisOddsCache cache = cache();
    cache.storeProviderMarketStatus(eventId, marketId, MarketStatus.OPEN);

    assertThat(cache().getRegisteredMarkets(eventId)).containsEntry(marketId, MarketStatus.OPEN);
  }

  @Test
  void terminalMarketRejectsLateProviderAndOddsUpdates() {
    EventId eventId = new EventId(UUID.randomUUID());
    MarketId marketId = new MarketId(UUID.randomUUID());
    SelectionId selectionId = new SelectionId(UUID.randomUUID());
    RedisOddsCache cache = cache();
    cache.storeOdds(eventId, marketId, selectionId, Odds.ofDecimal("1.85"));

    assertThat(cache.storeProviderMarketStatus(eventId, marketId, MarketStatus.CLOSED))
        .isEqualTo(MarketStatus.CLOSED);
    assertThat(cache.storeProviderMarketStatus(eventId, marketId, MarketStatus.OPEN))
        .isEqualTo(MarketStatus.CLOSED);
    cache.storeOdds(eventId, marketId, selectionId, Odds.ofDecimal("2.20"));

    assertThat(cache.getOdds(eventId, marketId, selectionId)).contains(Odds.ofDecimal("1.85"));
    assertThat(cache.getMarketStatus(eventId, marketId)).contains(MarketStatus.CLOSED);
    assertThat(redis.getExpire(CacheKeys.marketTerminal(eventId, marketId))).isEqualTo(-1);
  }

  private RedisOddsCache cache() {
    return new RedisOddsCache(
        redis,
        new ObjectMapper().findAndRegisterModules(),
        new CacheProperties(Duration.ofHours(24)));
  }
}
