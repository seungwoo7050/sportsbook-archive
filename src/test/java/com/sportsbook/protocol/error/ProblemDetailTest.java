package com.sportsbook.protocol.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ProblemDetailTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void errorCodeBuildsMinimalProblemDetail() {
    ProblemDetail detail = ErrorCode.ODDS_DRIFT.toProblemDetail();
    assertThat(detail.type()).isEqualTo(URI.create("https://sportsbook/errors/odds-drift"));
    assertThat(detail.title()).isEqualTo("Odds drift exceeds tolerance");
    assertThat(detail.status()).isEqualTo(409);
    assertThat(detail.errorCode()).isEqualTo("ODDS_DRIFT");
    assertThat(detail.detail()).isNull();
  }

  @Test
  void extensionsCarryRequestContext() {
    ProblemDetail detail =
        ErrorCode.EVENT_CLOSED.toProblemDetail(
            "Event is no longer open", URI.create("/api/v1/events/event-1"), "trace-1");
    assertThat(detail.detail()).isEqualTo("Event is no longer open");
    assertThat(detail.instance()).isEqualTo(URI.create("/api/v1/events/event-1"));
    assertThat(detail.correlationId()).isEqualTo("trace-1");
  }

  @Test
  void jsonOmitsNullExtensions() throws Exception {
    String json =
        mapper.writeValueAsString(
            ErrorCode.DUPLICATE_BET.toProblemDetail("request key already exists"));
    assertThat(json).doesNotContain("instance", "correlationId");
    assertThat(json).contains("\"errorCode\":\"DUPLICATE_BET\"");
  }

  @Test
  void populatedProblemDetailRoundTrips() throws Exception {
    ProblemDetail original =
        ErrorCode.EVENT_CLOSED.toProblemDetail(
            "Event is no longer open", URI.create("/api/v1/events/event-1"), "trace-1");
    assertThat(mapper.readValue(mapper.writeValueAsString(original), ProblemDetail.class))
        .isEqualTo(original);
  }
}
