package com.sportsbook.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.wallet.domain.WalletCaller;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class WalletSecurityBindingTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner().withUserConfiguration(BindingConfiguration.class);

  @Test
  void bindsEveryExactCallerProperty() {
    context
        .withPropertyValues(properties())
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              WalletSecurityProperties bound = application.getBean(WalletSecurityProperties.class);
              for (WalletCaller caller : WalletCaller.values()) {
                assertThat(bound.apiKey(caller)).isEqualTo(TestInternalApiKeys.key(caller));
              }
            });
  }

  @Test
  void failsStartupWhenOneCallerPropertyIsMissing() {
    context
        .withPropertyValues(properties()[0], properties()[1], properties()[2], properties()[3])
        .run(
            application -> {
              assertThat(application).hasFailed();
              assertThat(application.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalArgumentException.class);
            });
  }

  @Test
  void usesOnlyExactEnvironmentPlaceholders() throws IOException {
    PropertySource<?> source =
        new YamlPropertySourceLoader()
            .load("wallet", new ClassPathResource("application.yml"))
            .get(0);
    Map<String, String> placeholders =
        Map.of(
            "wallet.security.platform-api-key", "${WALLET_PLATFORM_API_KEY}",
            "wallet.security.gateway-api-key", "${WALLET_GATEWAY_API_KEY}",
            "wallet.security.betting-service-api-key", "${WALLET_BETTING_SERVICE_API_KEY}",
            "wallet.security.settlement-service-api-key", "${WALLET_SETTLEMENT_SERVICE_API_KEY}",
            "wallet.security.admin-api-key", "${WALLET_ADMIN_API_KEY}");
    placeholders.forEach(
        (name, placeholder) -> assertThat(source.getProperty(name)).isEqualTo(placeholder));
  }

  private String[] properties() {
    return new String[] {
      "wallet.security.platform-api-key=" + TestInternalApiKeys.key(WalletCaller.PLATFORM),
      "wallet.security.gateway-api-key=" + TestInternalApiKeys.key(WalletCaller.GATEWAY),
      "wallet.security.betting-service-api-key=" + TestInternalApiKeys.key(WalletCaller.BETTING),
      "wallet.security.settlement-service-api-key="
          + TestInternalApiKeys.key(WalletCaller.SETTLEMENT),
      "wallet.security.admin-api-key=" + TestInternalApiKeys.key(WalletCaller.ADMIN)
    };
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(WalletSecurityProperties.class)
  static class BindingConfiguration {}
}
