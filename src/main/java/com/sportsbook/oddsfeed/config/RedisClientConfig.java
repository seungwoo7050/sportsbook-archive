package com.sportsbook.oddsfeed.config;

import io.lettuce.core.resource.DnsResolver;
import io.lettuce.core.resource.DnsResolvers;
import io.lettuce.core.resource.SocketAddressResolver;
import org.springframework.boot.autoconfigure.data.redis.ClientResourcesBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RedisClientConfig {

  @Bean
  public ClientResourcesBuilderCustomizer redisDnsResolverCustomizer() {
    return dnsResolverCustomizer(DnsResolvers.JVM_DEFAULT);
  }

  static ClientResourcesBuilderCustomizer dnsResolverCustomizer(DnsResolver dnsResolver) {
    return builder -> builder.socketAddressResolver(SocketAddressResolver.create(dnsResolver));
  }
}
