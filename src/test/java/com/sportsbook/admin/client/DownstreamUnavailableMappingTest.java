package com.sportsbook.admin.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

class DownstreamUnavailableMappingTest {

  private final DownstreamFailureMapper mapper = new DownstreamFailureMapper();

  @Test
  void classifies5xxAsAnUnknownServerOutcome() {
    DownstreamUnavailableException mapped =
        mapped(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

    assertThat(mapped.reason()).isEqualTo(DownstreamUnavailableException.Reason.SERVER_ERROR);
    assertThat(mapped.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void distinguishesTimeoutFromOtherTransportFailures() {
    DownstreamUnavailableException timeout =
        mapped(new ResourceAccessException("timed out", new SocketTimeoutException()));
    DownstreamUnavailableException transport =
        mapped(new ResourceAccessException("connection lost", new ConnectException()));

    assertThat(timeout.reason()).isEqualTo(DownstreamUnavailableException.Reason.TIMEOUT);
    assertThat(timeout.status()).isNull();
    assertThat(transport.reason()).isEqualTo(DownstreamUnavailableException.Reason.TRANSPORT);
  }

  @Test
  void recognizesReadTimeoutsWrappedDuringBodyExtraction() {
    DownstreamUnavailableException timeout =
        mapped(new RestClientException("extract response", new SocketTimeoutException()));

    assertThat(timeout.reason()).isEqualTo(DownstreamUnavailableException.Reason.TIMEOUT);
    assertThat(timeout.status()).isNull();
  }

  @Test
  void neverRetriesAnAmbiguousMutationAutomatically() {
    AtomicInteger attempts = new AtomicInteger();

    mapped(new ResourceAccessException("lost response", new SocketTimeoutException()), attempts);

    assertThat(attempts).hasValue(1);
  }

  private DownstreamUnavailableException mapped(RuntimeException failure) {
    return mapped(failure, new AtomicInteger());
  }

  private DownstreamUnavailableException mapped(RuntimeException failure, AtomicInteger attempts) {
    return catchThrowableOfType(
        () ->
            mapper.execute(
                (Supplier<Object>)
                    () -> {
                      attempts.incrementAndGet();
                      throw failure;
                    }),
        DownstreamUnavailableException.class);
  }
}
