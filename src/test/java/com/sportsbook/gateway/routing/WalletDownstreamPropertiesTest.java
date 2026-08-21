package com.sportsbook.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class WalletDownstreamPropertiesTest {

  private static final String KEY = "01234567890123456789012345678901";

  @Test
  void acceptsARequiredCredentialWithoutExposingIt() {
    valid()
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean("walletApiKey")).isEqualTo(KEY);
              assertThat(context.getBean(WalletDownstreamProperties.class).toString())
                  .doesNotContain(KEY)
                  .contains("<redacted>");
            });
  }

  @Test
  void rejectsMissingBlankAndShortCredentialsWhenRequired() {
    assertRejected(runner("gateway.downstream.wallet.uri=http://wallet.internal"));
    assertRejected(
        runner(
            "gateway.downstream.wallet.uri=http://wallet.internal",
            "gateway.downstream.wallet.api-key= "));
    assertRejected(
        runner(
            "gateway.downstream.wallet.uri=http://wallet.internal",
            "gateway.downstream.wallet.api-key=too-short"));
  }

  @Test
  void rejectsUnsafeWalletBaseUris() {
    for (String uri :
        new String[] {
          "ftp://wallet.internal",
          "http://user@wallet.internal",
          "http://wallet.internal/base",
          "http://wallet.internal?query=1"
        }) {
      assertRejected(
          runner(
              "gateway.downstream.wallet.uri=" + uri, "gateway.downstream.wallet.api-key=" + KEY));
    }
  }

  private static ApplicationContextRunner valid() {
    return runner(
        "gateway.downstream.wallet.uri=http://wallet.internal",
        "gateway.downstream.wallet.api-key=" + KEY);
  }

  private static ApplicationContextRunner runner(String... properties) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(PropertyConfiguration.class)
        .withPropertyValues(properties);
  }

  private static void assertRejected(ApplicationContextRunner runner) {
    runner.run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(WalletDownstreamProperties.class)
  static class PropertyConfiguration {
    @Bean
    String walletApiKey(WalletDownstreamProperties properties) {
      return properties.requiredApiKey();
    }
  }
}
