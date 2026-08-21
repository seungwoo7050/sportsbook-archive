package com.sportsbook.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.codec.ByteArrayCodec;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

class RateLimitRedisConfigurationTest {

  private RedisClient client;

  @AfterEach
  void shutdownClient() {
    if (client != null) {
      client.shutdown();
    }
  }

  @Test
  void configuresReconnectAndFixedConnectionBounds() {
    client = create("localhost", 6379);
    ClientOptions options = client.getOptions();
    TimeoutOptions timeouts = options.getTimeoutOptions();

    assertThat(options.isAutoReconnect()).isTrue();
    assertThat(options.getDisconnectedBehavior())
        .isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    assertThat(options.getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofMillis(300));
    assertThat(client.getDefaultTimeout()).isEqualTo(Duration.ofMillis(500));
    assertThat(timeouts.isTimeoutCommands()).isTrue();
    assertThat(timeouts.getSource().getTimeUnit().toMillis(timeouts.getSource().getTimeout(null)))
        .isEqualTo(500);
  }

  @Test
  void unreachableRedisFailsWithinTheConnectionBound() {
    client = create("192.0.2.1", 6379);

    assertTimeoutPreemptively(
        Duration.ofSeconds(2),
        () -> assertThrows(RedisException.class, () -> client.connect(ByteArrayCodec.INSTANCE)));
  }

  private static RedisClient create(String host, int port) {
    RedisProperties properties = new RedisProperties();
    properties.setHost(host);
    properties.setPort(port);
    return new RateLimitRedisConfiguration().rateLimitRedisClient(properties);
  }
}
