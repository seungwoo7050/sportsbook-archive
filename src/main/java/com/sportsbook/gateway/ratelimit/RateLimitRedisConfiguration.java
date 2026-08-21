package com.sportsbook.gateway.ratelimit;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class RateLimitRedisConfiguration {

  static final Duration CONNECT_TIMEOUT = Duration.ofMillis(300);
  static final Duration COMMAND_TIMEOUT = Duration.ofMillis(500);

  @Bean(destroyMethod = "shutdown")
  RedisClient rateLimitRedisClient(RedisProperties properties) {
    RedisURI.Builder uri =
        RedisURI.builder()
            .withHost(properties.getHost())
            .withPort(properties.getPort())
            .withDatabase(properties.getDatabase())
            .withSsl(properties.getSsl().isEnabled())
            .withTimeout(COMMAND_TIMEOUT);
    if (StringUtils.hasText(properties.getPassword())) {
      if (StringUtils.hasText(properties.getUsername())) {
        uri.withAuthentication(properties.getUsername(), properties.getPassword().toCharArray());
      } else {
        uri.withPassword(properties.getPassword().toCharArray());
      }
    }

    RedisClient client = RedisClient.create(uri.build());
    client.setOptions(
        ClientOptions.builder()
            .autoReconnect(true)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .socketOptions(SocketOptions.builder().connectTimeout(CONNECT_TIMEOUT).build())
            .timeoutOptions(TimeoutOptions.enabled(COMMAND_TIMEOUT))
            .build());
    return client;
  }
}
