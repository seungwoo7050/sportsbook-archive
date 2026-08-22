package com.sportsbook.admin.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("admin.security")
public record AdminNetworkProperties(List<String> ipAllowlist, List<String> trustedProxyCidrs) {

  public AdminNetworkProperties {
    ipAllowlist = validated(ipAllowlist, true, "ADMIN_IP_ALLOWLIST");
    trustedProxyCidrs = validated(trustedProxyCidrs, false, "ADMIN_TRUSTED_PROXY_CIDRS");
  }

  private static List<String> validated(
      List<String> configured, boolean required, String settingName) {
    List<String> values =
        configured == null
            ? List.of()
            : configured.stream().filter(value -> value != null && !value.isBlank()).toList();
    if (required && values.isEmpty()) {
      throw new IllegalArgumentException(settingName + " must contain at least one CIDR");
    }
    try {
      values.forEach(CidrBlock::parse);
    } catch (IllegalArgumentException invalidCidr) {
      throw new IllegalArgumentException(settingName + " contains an invalid CIDR", invalidCidr);
    }
    return List.copyOf(values);
  }
}
