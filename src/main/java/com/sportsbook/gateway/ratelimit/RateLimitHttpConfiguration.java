package com.sportsbook.gateway.ratelimit;

import com.sportsbook.gateway.error.GatewayProblemWriter;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RateLimitHttpConfiguration {

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      RateLimitProperties properties,
      RateLimitKeyResolver keys,
      RateLimiterService limiter,
      GatewayProblemWriter problems) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new RateLimitFilter(properties, keys, limiter, problems));
    registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 10);
    return registration;
  }
}
