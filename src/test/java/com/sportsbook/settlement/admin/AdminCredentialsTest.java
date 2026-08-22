package com.sportsbook.settlement.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AdminCredentialsTest {

  private static final String SECRET = "abcdef0123456789abcdef0123456789";
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(CredentialsConfiguration.class);

  @Test
  void rejectsMissingOrShortAdminSecrets() {
    runner.run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues("settlement.admin.api-key=too-short")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void exposesOnlyFixedHeadersAndRedactedText() {
    runner
        .withPropertyValues("settlement.admin.api-key=" + SECRET)
        .run(
            context -> {
              AdminCredentials credentials = context.getBean(AdminCredentials.class);
              assertThat(AdminCredentials.CALLER).isEqualTo("admin-api");
              assertThat(AdminCredentials.SERVICE_HEADER).isEqualTo("X-Service-Name");
              assertThat(AdminCredentials.API_KEY_HEADER).isEqualTo("X-API-Key");
              assertThat(credentials.toString()).contains("<redacted>").doesNotContain(SECRET);
            });
  }

  @Test
  void productionConfigurationUsesOnlyTheSettlementAdminSecret() throws Exception {
    String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

    assertThat(yaml).contains("SETTLEMENT_ADMIN_API_KEY");
    assertThat(yaml).doesNotContain("WALLET_ADMIN_API_KEY", "BETTING_ADMIN_API_KEY");
  }

  @EnableConfigurationProperties(AdminCredentials.class)
  static class CredentialsConfiguration {}
}
