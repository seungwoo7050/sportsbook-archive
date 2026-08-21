package com.sportsbook.oddsfeed.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisURI;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolver;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.ClientResourcesBuilderCustomizer;

class RedisClientConfigTest {

  @Test
  void resolvesLocalhostToAConcreteAddress() {
    DefaultClientResources resources =
        clientResources(new RedisClientConfig().redisDnsResolverCustomizer());

    try {
      InetSocketAddress resolved =
          (InetSocketAddress)
              resources.socketAddressResolver().resolve(RedisURI.create("localhost", 6379));

      assertThat(resolved.isUnresolved()).isFalse();
      assertThat(resolved.getAddress().isLoopbackAddress()).isTrue();
    } finally {
      resources.shutdown().syncUninterruptibly();
    }
  }

  @Test
  void resolvesHostnamesForEveryConnectionAttempt() {
    AtomicInteger resolutions = new AtomicInteger();
    DnsResolver changingResolver =
        hostname -> {
          assertThat(hostname).isEqualTo("redis.internal");
          String endpoint = resolutions.getAndIncrement() == 0 ? "192.0.2.10" : "192.0.2.11";
          return new InetAddress[] {InetAddress.getByName(endpoint)};
        };
    DefaultClientResources resources =
        clientResources(RedisClientConfig.dnsResolverCustomizer(changingResolver));
    RedisURI redisUri = RedisURI.create("redis.internal", 6379);

    try {
      InetSocketAddress first =
          (InetSocketAddress) resources.socketAddressResolver().resolve(redisUri);
      InetSocketAddress second =
          (InetSocketAddress) resources.socketAddressResolver().resolve(redisUri);

      assertThat(first.getAddress().getHostAddress()).isEqualTo("192.0.2.10");
      assertThat(second.getAddress().getHostAddress()).isEqualTo("192.0.2.11");
      assertThat(resolutions).hasValue(2);
    } finally {
      resources.shutdown().syncUninterruptibly();
    }
  }

  private static DefaultClientResources clientResources(
      ClientResourcesBuilderCustomizer customizer) {
    DefaultClientResources.Builder builder = DefaultClientResources.builder();
    customizer.customize(builder);
    return builder.build();
  }
}
