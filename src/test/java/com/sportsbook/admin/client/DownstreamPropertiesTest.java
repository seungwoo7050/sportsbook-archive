package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DownstreamPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(HttpOriginsConfiguration.class);

  @Test
  void bindsFourHttpOriginsAndPositiveTimeouts() {
    contextRunner
        .withPropertyValues(validProperties())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              DownstreamProperties properties = context.getBean(DownstreamProperties.class);
              assertThat(properties.walletBaseUrl().toString()).isEqualTo("http://wallet:8081");
              assertThat(properties.riskBaseUrl().toString()).isEqualTo("http://risk:8083");
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(200));
              assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
            });
  }

  @Test
  void rejectsMissingAndNonHttpOrigins() {
    contextRunner.run(context -> assertThat(context).hasFailed());
    String[] invalid = validProperties();
    invalid[0] = "admin.downstream.wallet-base-url=ftp://wallet:21";
    contextRunner.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsZeroOrNegativeTimeouts() {
    String[] invalid = validProperties();
    invalid[4] = "admin.downstream.connect-timeout=0ms";
    contextRunner.withPropertyValues(invalid).run(context -> assertThat(context).hasFailed());
  }

  private static String[] validProperties() {
    return new String[] {
      "admin.downstream.wallet-base-url=http://wallet:8081",
      "admin.downstream.risk-base-url=http://risk:8083",
      "admin.downstream.odds-feed-base-url=http://odds:8085",
      "admin.downstream.settlement-base-url=http://settlement:8084",
      "admin.downstream.connect-timeout=200ms",
      "admin.downstream.read-timeout=2s"
    };
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(DownstreamProperties.class)
  static class HttpOriginsConfiguration {}
}
