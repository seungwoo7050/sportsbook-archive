package com.sportsbook.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class BettingDownstreamPropertiesTest {

  private static final String KEY = "01234567890123456789012345678901";

  @Test
  void acceptsARequiredCredentialWithoutExposingIt() {
    runner("gateway.downstream.betting-api-key=" + KEY)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean("bettingApiKey")).isEqualTo(KEY);
              assertThat(context.getBean(BettingDownstreamProperties.class).toString())
                  .doesNotContain(KEY)
                  .contains("<redacted>");
            });
  }

  @Test
  void rejectsMissingBlankAndShortCredentialsWhenRequired() {
    assertRejected(runner());
    assertRejected(runner("gateway.downstream.betting-api-key= "));
    assertRejected(runner("gateway.downstream.betting-api-key=too-short"));
  }

  private static ApplicationContextRunner runner(String... properties) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(PropertyConfiguration.class)
        .withPropertyValues("gateway.downstream.betting-uri=http://betting.internal")
        .withPropertyValues(properties);
  }

  private static void assertRejected(ApplicationContextRunner runner) {
    runner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(IllegalArgumentException.class)
              .hasRootCauseMessage("GATEWAY_BETTING_API_KEY must contain at least 32 characters");
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(BettingDownstreamProperties.class)
  static class PropertyConfiguration {
    @Bean
    String bettingApiKey(BettingDownstreamProperties properties) {
      return properties.requiredApiKey();
    }
  }
}
