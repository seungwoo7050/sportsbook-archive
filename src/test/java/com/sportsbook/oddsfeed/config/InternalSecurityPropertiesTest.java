package com.sportsbook.oddsfeed.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InternalSecurityPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Test
  void acceptsASecretWithAtLeastThirtyTwoCharacters() {
    String secret = "0123456789abcdef0123456789abcdef";

    assertThat(new InternalSecurityProperties(secret).apiKey()).isEqualTo(secret);
  }

  @Test
  void rejectsMissingBlankAndShortSecrets() {
    assertThatThrownBy(() -> new InternalSecurityProperties(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ADMIN_API_INTERNAL_KEY");
    assertThatThrownBy(() -> new InternalSecurityProperties("too-short"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32");
    assertThatThrownBy(() -> new InternalSecurityProperties(" ".repeat(32)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bindingEnforcesTheStartupBoundary() {
    contextRunner.run(context -> assertThat(context).hasFailed());
    contextRunner
        .withPropertyValues("oddsfeed.security.internal.api-key=too-short")
        .run(context -> assertThat(context).hasFailed());
    contextRunner
        .withPropertyValues("oddsfeed.security.internal.api-key=0123456789abcdef0123456789abcdef")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @EnableConfigurationProperties(InternalSecurityProperties.class)
  static class PropertiesConfiguration {}
}
