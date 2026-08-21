package com.sportsbook.gateway.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("gateway.ratelimit")
public record RateLimitProperties(
    boolean enabled,
    @Valid @NotNull Limit user,
    @Valid @NotNull Limit ip,
    List<String> trustedProxyCidrs) {

  public RateLimitProperties {
    trustedProxyCidrs = trustedProxyCidrs == null ? List.of() : List.copyOf(trustedProxyCidrs);
    trustedProxyCidrs.forEach(IpAddressMatcher::new);
  }

  public record Limit(@Positive long capacity, @NotNull Duration refillPeriod) {

    @AssertTrue(message = "refill period must be positive")
    public boolean isRefillPeriodPositive() {
      return refillPeriod == null || !refillPeriod.isZero() && !refillPeriod.isNegative();
    }
  }
}
