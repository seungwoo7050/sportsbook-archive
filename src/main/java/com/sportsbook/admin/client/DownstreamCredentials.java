package com.sportsbook.admin.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("admin.downstream.credentials")
public record DownstreamCredentials(
    @NotBlank @Size(min = 32) String walletApiKey,
    @NotBlank @Size(min = 32) String riskApiKey,
    @NotBlank @Size(min = 32) String oddsFeedApiKey,
    @NotBlank @Size(min = 32) String settlementApiKey) {

  public DownstreamCredentials {
    var configured =
        Stream.of(walletApiKey, riskApiKey, oddsFeedApiKey, settlementApiKey)
            .filter(Objects::nonNull)
            .toList();
    if (new HashSet<>(configured).size() != configured.size()) {
      throw new IllegalArgumentException("Admin downstream API keys must be distinct");
    }
  }

  @Override
  public String toString() {
    return "DownstreamCredentials[REDACTED]";
  }
}
