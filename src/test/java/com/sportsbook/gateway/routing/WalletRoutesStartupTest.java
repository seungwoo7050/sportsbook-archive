package com.sportsbook.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.gateway.error.GatewayProblemWriter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class WalletRoutesStartupTest {

  private final WebApplicationContextRunner context =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
          .withUserConfiguration(WalletRoutes.class)
          .withBean(DownstreamRequestSanitizer.class)
          .withBean(IdentityForwarding.class)
          .withBean(ObjectMapper.class, ObjectMapper::new)
          .withBean(GatewayProblemWriter.class)
          .withBean(TraceForwarding.class)
          .withBean(DownstreamFailureBoundary.class)
          .withPropertyValues("gateway.downstream.wallet.uri=http://wallet.internal");

  @Test
  void failsStartupWhenWalletKeyIsMissing() {
    assertRejected(context);
  }

  @Test
  void failsStartupWhenWalletKeyIsShort() {
    assertRejected(context.withPropertyValues("gateway.downstream.wallet.api-key=short"));
  }

  private static void assertRejected(WebApplicationContextRunner runner) {
    runner.run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(IllegalArgumentException.class)
              .hasRootCauseMessage("GATEWAY_WALLET_API_KEY must contain at least 32 characters");
        });
  }
}
