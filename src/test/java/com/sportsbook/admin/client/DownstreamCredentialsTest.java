package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DownstreamCredentialsTest {

  private static final String WALLET = "wallet-admin-test-key-000000000001";
  private static final String RISK = "risk-admin-test-key-00000000000002";
  private static final String ODDS = "odds-admin-test-key-00000000000003";
  private static final String SETTLEMENT = "settlement-admin-test-key-000000004";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(CredentialsConfiguration.class);

  @Test
  void bindsFourDistinctLongCredentialsWithoutRenderingThem() {
    contextRunner
        .withPropertyValues(validProperties())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              DownstreamCredentials credentials = context.getBean(DownstreamCredentials.class);
              assertThat(credentials.walletApiKey()).isEqualTo(WALLET);
              assertThat(credentials.riskApiKey()).isEqualTo(RISK);
              assertThat(credentials.oddsFeedApiKey()).isEqualTo(ODDS);
              assertThat(credentials.settlementApiKey()).isEqualTo(SETTLEMENT);
              assertThat(credentials.toString())
                  .isEqualTo("DownstreamCredentials[REDACTED]")
                  .doesNotContain(WALLET, RISK, ODDS, SETTLEMENT);
            });
  }

  @Test
  void rejectsMissingAndShortCredentials() {
    contextRunner.run(context -> assertThat(context).hasFailed());
    contextRunner
        .withPropertyValues(
            "admin.downstream.credentials.wallet-api-key=short",
            "admin.downstream.credentials.risk-api-key=" + RISK,
            "admin.downstream.credentials.odds-feed-api-key=" + ODDS,
            "admin.downstream.credentials.settlement-api-key=" + SETTLEMENT)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsCredentialReuseAcrossProviders() {
    contextRunner
        .withPropertyValues(
            "admin.downstream.credentials.wallet-api-key=" + WALLET,
            "admin.downstream.credentials.risk-api-key=" + WALLET,
            "admin.downstream.credentials.odds-feed-api-key=" + ODDS,
            "admin.downstream.credentials.settlement-api-key=" + SETTLEMENT)
        .run(context -> assertThat(context).hasFailed());
  }

  private static String[] validProperties() {
    return new String[] {
      "admin.downstream.credentials.wallet-api-key=" + WALLET,
      "admin.downstream.credentials.risk-api-key=" + RISK,
      "admin.downstream.credentials.odds-feed-api-key=" + ODDS,
      "admin.downstream.credentials.settlement-api-key=" + SETTLEMENT
    };
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(DownstreamCredentials.class)
  static class CredentialsConfiguration {}
}
