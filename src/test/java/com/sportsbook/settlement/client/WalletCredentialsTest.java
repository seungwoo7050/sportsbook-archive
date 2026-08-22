package com.sportsbook.settlement.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WalletCredentialsTest {

  private static final String SECRET = "0123456789abcdef0123456789abcdef";
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(CredentialsConfiguration.class);

  @Test
  void failsStartupWhenSettlementCredentialIsMissingOrShort() {
    runner.run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("settlement.wallet.api-key=too-short")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void exposesOnlyCallerIdentityAndRedactedCredentialText() {
    runner
        .withPropertyValues("settlement.wallet.api-key=" + SECRET)
        .run(
            context -> {
              WalletCredentials credentials = context.getBean(WalletCredentials.class);
              assertThat(WalletCredentials.CALLER).isEqualTo("settlement-service");
              assertThat(credentials.apiKey()).isEqualTo(SECRET);
              assertThat(credentials.toString()).doesNotContain(SECRET).contains("<redacted>");
            });
  }

  @Test
  void productionConfigurationBindsNoOtherServiceSecret() throws Exception {
    String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

    assertThat(yaml).contains("SETTLEMENT_WALLET_API_KEY");
    assertThat(yaml)
        .doesNotContain("WALLET_PLATFORM_API_KEY", "WALLET_ADMIN_API_KEY", "WALLET_BETTING");
  }

  @EnableConfigurationProperties(WalletCredentials.class)
  static class CredentialsConfiguration {}
}
