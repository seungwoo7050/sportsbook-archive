package com.sportsbook.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RateLimitPropertiesTest {

  @Test
  void rejectsZeroAndNegativeCapacity() {
    assertRejected(valid("gateway.ratelimit.user.capacity=0"));
    assertRejected(valid("gateway.ratelimit.ip.capacity=-1"));
  }

  @Test
  void rejectsMissingAndNonPositiveRefillPeriods() {
    assertRejected(
        runner(
            "gateway.ratelimit.user.capacity=120",
            "gateway.ratelimit.ip.capacity=60",
            "gateway.ratelimit.ip.refill-period=1m"));
    assertRejected(valid("gateway.ratelimit.user.refill-period=0s"));
    assertRejected(valid("gateway.ratelimit.ip.refill-period=-1s"));
  }

  @Test
  void rejectsInvalidTrustedProxyCidr() {
    assertRejected(valid("gateway.ratelimit.trusted-proxy-cidrs=not-a-cidr"));
  }

  private static ApplicationContextRunner valid(String... overrides) {
    ApplicationContextRunner runner =
        runner(
            "gateway.ratelimit.enabled=true",
            "gateway.ratelimit.user.capacity=120",
            "gateway.ratelimit.user.refill-period=1m",
            "gateway.ratelimit.ip.capacity=60",
            "gateway.ratelimit.ip.refill-period=1m");
    return runner.withPropertyValues(overrides);
  }

  private static ApplicationContextRunner runner(String... properties) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
        .withUserConfiguration(PropertyConfiguration.class)
        .withPropertyValues(properties);
  }

  private static void assertRejected(ApplicationContextRunner runner) {
    runner.run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(RateLimitProperties.class)
  static class PropertyConfiguration {}
}
