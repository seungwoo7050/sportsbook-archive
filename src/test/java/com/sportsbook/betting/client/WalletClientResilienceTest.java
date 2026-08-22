package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.value.Money;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WalletClientResilienceTest {

  @Test
  void protectsEveryWalletLifecycleCall() throws NoSuchMethodException {
    assertFallback(
        WalletClient.class.getMethod("debit", UUID.class, UUID.class, Money.class),
        "debitFallback");
    assertFallback(
        WalletClient.class.getMethod("findDebit", UUID.class, UUID.class, Money.class),
        "findDebitFallback");
    assertFallback(
        WalletClient.class.getMethod("refund", UUID.class, UUID.class, Money.class),
        "refundFallback");
  }

  private void assertFallback(Method method, String expected) {
    CircuitBreaker breaker = method.getAnnotation(CircuitBreaker.class);
    assertThat(breaker).isNotNull();
    assertThat(breaker.name()).isEqualTo("walletClient");
    assertThat(breaker.fallbackMethod()).isEqualTo(expected);
  }
}
