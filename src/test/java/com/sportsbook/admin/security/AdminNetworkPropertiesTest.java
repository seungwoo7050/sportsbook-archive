package com.sportsbook.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AdminNetworkPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(NetworkConfiguration.class);

  @Test
  void bindsValidatedIpv4AndIpv6Boundaries() {
    contextRunner
        .withPropertyValues(
            "admin.security.ip-allowlist=127.0.0.1/32,::1/128",
            "admin.security.trusted-proxy-cidrs=10.0.0.0/8,fd00::/8")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              AdminNetworkProperties properties = context.getBean(AdminNetworkProperties.class);
              assertThat(properties.ipAllowlist()).containsExactly("127.0.0.1/32", "::1/128");
              assertThat(properties.trustedProxyCidrs()).containsExactly("10.0.0.0/8", "fd00::/8");
            });
  }

  @Test
  void rejectsAMissingAllowlistAndMalformedCidrs() {
    contextRunner.run(context -> assertThat(context).hasFailed());
    contextRunner
        .withPropertyValues("admin.security.ip-allowlist=10.0.0.0/33")
        .run(context -> assertThat(context).hasFailed());
    contextRunner
        .withPropertyValues(
            "admin.security.ip-allowlist=127.0.0.1/32",
            "admin.security.trusted-proxy-cidrs=proxy.internal/24")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void makesConfiguredListsImmutable() {
    AdminNetworkProperties properties =
        new AdminNetworkProperties(List.of("127.0.0.1/32"), List.of());

    assertThatThrownBy(() -> properties.ipAllowlist().add("10.0.0.0/8"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AdminNetworkProperties.class)
  static class NetworkConfiguration {}
}
