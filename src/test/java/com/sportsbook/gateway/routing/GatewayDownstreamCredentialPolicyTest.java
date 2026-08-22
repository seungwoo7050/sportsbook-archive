package com.sportsbook.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class GatewayDownstreamCredentialPolicyTest {

  private static final String BETTING_KEY = "betting-downstream-credential-0001";
  private static final String WALLET_KEY = "wallet-downstream-credential-00002";
  private static final String SHARED_KEY = "shared-downstream-credential-00002";

  @Test
  void acceptsDistinctCredentialsWithoutRenderingSecrets() {
    runner(BETTING_KEY, WALLET_KEY)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(BettingDownstreamProperties.class).toString())
                  .doesNotContain(BETTING_KEY, WALLET_KEY)
                  .contains("<redacted>");
              assertThat(context.getBean(WalletDownstreamProperties.class).toString())
                  .doesNotContain(BETTING_KEY, WALLET_KEY)
                  .contains("<redacted>");
            });
  }

  @Test
  void rejectsAReusedCredentialWithoutRenderingIt() {
    runner(SHARED_KEY, SHARED_KEY)
        .run(
            context -> {
              assertThat(context).hasFailed();
              Throwable root = rootCause(context.getStartupFailure());
              assertThat(root)
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessage("Gateway downstream API keys must be distinct");
              assertThat(root.getMessage()).doesNotContain(SHARED_KEY);
            });
  }

  private static WebApplicationContextRunner runner(String bettingKey, String walletKey) {
    return new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(GatewayDownstreamCredentialPolicy.class)
        .withPropertyValues(
            "gateway.downstream.betting-uri=http://betting.internal",
            "gateway.downstream.betting-api-key=" + bettingKey,
            "gateway.downstream.wallet.uri=http://wallet.internal",
            "gateway.downstream.wallet.api-key=" + walletKey);
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable root = failure;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root;
  }
}
