package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AdminJwtPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(JwtPropertiesConfiguration.class);

  @Test
  void requiresAPublicKey() {
    contextRunner.run(context -> assertThat(context).hasFailed());
    contextRunner
        .withPropertyValues("admin.security.jwt.public-key=   ")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void bindsARequiredKeyAndOptionalExactIssuer() {
    contextRunner
        .withPropertyValues(
            "admin.security.jwt.public-key=test-public-key",
            "admin.security.jwt.issuer=https://iam.example.test")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              AdminJwtProperties properties = context.getBean(AdminJwtProperties.class);
              assertThat(properties.publicKey()).isEqualTo("test-public-key");
              assertThat(properties.expectedIssuer()).contains("https://iam.example.test");
            });
  }

  @Test
  void treatsABlankIssuerAsUnconfigured() {
    contextRunner
        .withPropertyValues(
            "admin.security.jwt.public-key=test-public-key", "admin.security.jwt.issuer=")
        .run(
            context ->
                assertThat(context.getBean(AdminJwtProperties.class).expectedIssuer()).isEmpty());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AdminJwtProperties.class)
  static class JwtPropertiesConfiguration {}
}
