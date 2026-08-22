package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Money;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskClientResilienceTest {

  @Test
  void protectsEveryRiskLifecycleCall() throws NoSuchMethodException {
    assertFallback(
        RiskClient.class.getMethod("reserve", UUID.class, UUID.class, Money.class, List.class),
        "reserveFallback");
    assertFallback(
        RiskClient.class.getMethod("commit", UUID.class, String.class), "commitFallback");
    assertFallback(RiskClient.class.getMethod("release", UUID.class), "releaseFallback");
  }

  private void assertFallback(Method method, String expected) {
    CircuitBreaker breaker = method.getAnnotation(CircuitBreaker.class);
    assertThat(breaker).isNotNull();
    assertThat(breaker.name()).isEqualTo("riskClient");
    assertThat(breaker.fallbackMethod()).isEqualTo(expected);
  }
}
