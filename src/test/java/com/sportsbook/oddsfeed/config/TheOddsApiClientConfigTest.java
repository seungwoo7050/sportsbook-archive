package com.sportsbook.oddsfeed.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

class TheOddsApiClientConfigTest {

  private static final RealProperties PROPERTIES =
      new RealProperties(
          "key",
          "https://odds.example",
          List.of("soccer_epl"),
          new RealProperties.RateLimit(1),
          500,
          60);

  @Test
  void webClientUsesTheConfiguredBaseUrl() {
    AtomicReference<URI> requested = new AtomicReference<>();
    var configured = new TheOddsApiClientConfig().theOddsWebClient(PROPERTIES);
    var client =
        configured
            .mutate()
            .exchangeFunction(
                request -> {
                  requested.set(request.url());
                  return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
                })
            .build();

    client.get().uri("/status").retrieve().toBodilessEntity().block(Duration.ofSeconds(1));

    assertThat(requested.get()).isEqualTo(URI.create("https://odds.example/status"));
  }

  @Test
  void rateLimiterUsesTheConfiguredBudget() {
    var clock = Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC);
    var limiter = new TheOddsApiClientConfig().theOddsRateLimiter(PROPERTIES, clock);

    assertThat(limiter.tryAcquire()).isTrue();
    assertThat(limiter.tryAcquire()).isFalse();
  }
}
