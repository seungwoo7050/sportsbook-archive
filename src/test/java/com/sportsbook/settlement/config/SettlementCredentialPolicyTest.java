package com.sportsbook.settlement.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.settlement.admin.AdminCredentials;
import com.sportsbook.settlement.client.WalletCredentials;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class SettlementCredentialPolicyTest {

  private static final String ADMIN = "admin-0123456789abcdef0123456789ab";
  private static final String WALLET = "wallet-0123456789abcdef0123456789a";
  private static final String REUSED = "reused-0123456789abcdef0123456789";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(CredentialConfiguration.class);

  @Test
  void acceptsDistinctCredentialsWithoutExposingTheirValues() {
    runner
        .withPropertyValues(
            "settlement.admin.api-key=" + ADMIN, "settlement.wallet.api-key=" + WALLET)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(AdminCredentials.class).toString())
                  .doesNotContain(ADMIN)
                  .contains("<redacted>");
              assertThat(context.getBean(WalletCredentials.class).toString())
                  .doesNotContain(WALLET)
                  .contains("<redacted>");
              assertThat(context.getBean(SettlementCredentialPolicy.class).toString())
                  .doesNotContain(ADMIN, WALLET);
            });
  }

  @Test
  void rejectsASecretReusedAcrossAdminAndWalletDirections() {
    runner
        .withPropertyValues(
            "settlement.admin.api-key=" + REUSED, "settlement.wallet.api-key=" + REUSED)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("Settlement admin and Wallet credentials must be distinct");
              assertThat(context.getStartupFailure().toString()).doesNotContain(REUSED);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties({AdminCredentials.class, WalletCredentials.class})
  @Import(SettlementCredentialPolicy.class)
  static class CredentialConfiguration {}
}
