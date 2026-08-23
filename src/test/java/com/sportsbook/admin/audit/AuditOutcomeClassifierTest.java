package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.sportsbook.admin.api.AdminRequestException;
import com.sportsbook.admin.client.DownstreamContract;
import com.sportsbook.admin.client.DownstreamFailureMapper;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class AuditOutcomeClassifierTest {

  private final AuditOutcomeClassifier classifier = new AuditOutcomeClassifier();
  private final DownstreamFailureMapper failures = new DownstreamFailureMapper();

  @Test
  void classifiesResultsByTheirActualHttpStatus() {
    assertThat(classifier.result("ok").outcome()).isEqualTo(AuditOutcome.SUCCESS);
    assertThat(classifier.result(ResponseEntity.accepted().build()).outcome())
        .isEqualTo(AuditOutcome.SUCCESS);
    assertThat(classifier.result(ResponseEntity.badRequest().build()).outcome())
        .isEqualTo(AuditOutcome.FAILED);
    assertThat(
            classifier
                .result(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build())
                .outcome())
        .isEqualTo(AuditOutcome.UNKNOWN);
  }

  @Test
  void treatsOnlyExplicit4xxAndLocalDenialsAsFailed() {
    Throwable downstream4xx = mapped(new HttpClientErrorException(HttpStatus.CONFLICT));

    assertThat(classifier.failure(downstream4xx).outcome()).isEqualTo(AuditOutcome.FAILED);
    assertThat(classifier.failure(new AccessDeniedException("denied")).outcome())
        .isEqualTo(AuditOutcome.FAILED);
    assertThat(classifier.failure(new IllegalArgumentException("invalid")).httpStatus())
        .isEqualTo(400);
    assertThat(classifier.failure(new AdminRequestException("invalid header")))
        .isEqualTo(new AuditOutcomeClassifier.AuditDecision(AuditOutcome.FAILED, 400));
  }

  @Test
  void treatsAmbiguousAndMalformedMutationOutcomesAsUnknown() {
    Throwable serverError = mapped(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
    Throwable timeout =
        mapped(new ResourceAccessException("timeout", new SocketTimeoutException()));
    Throwable malformed =
        catchThrowable(
            () ->
                DownstreamContract.requireBody(
                    ResponseEntity.ok().build(), HttpStatus.OK, value -> true, "missing receipt"));

    assertThat(classifier.failure(serverError).outcome()).isEqualTo(AuditOutcome.UNKNOWN);
    assertThat(classifier.failure(timeout).httpStatus()).isEqualTo(504);
    assertThat(classifier.failure(malformed).outcome()).isEqualTo(AuditOutcome.UNKNOWN);
    assertThat(classifier.failure(new IllegalStateException("unexpected")).outcome())
        .isEqualTo(AuditOutcome.UNKNOWN);
  }

  @Test
  void containsNoRemovedVoidOrReplayActions() {
    assertThat(Arrays.stream(AdminAction.values()).map(Enum::name))
        .noneMatch(name -> name.contains("VOID") || name.contains("REPLAY"));
  }

  private Throwable mapped(RuntimeException source) {
    return catchThrowable(
        () ->
            failures.execute(
                (Supplier<Object>)
                    () -> {
                      throw source;
                    }));
  }
}
