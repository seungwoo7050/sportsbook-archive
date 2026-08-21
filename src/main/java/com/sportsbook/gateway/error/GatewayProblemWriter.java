package com.sportsbook.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.error.ProblemDetail;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public final class GatewayProblemWriter {

  private final ObjectMapper objectMapper;
  private final ObjectProvider<Tracer> tracerProvider;

  public GatewayProblemWriter(ObjectMapper objectMapper, ObjectProvider<Tracer> tracerProvider) {
    this.objectMapper = objectMapper;
    this.tracerProvider = tracerProvider;
  }

  public void write(
      HttpServletRequest request, HttpServletResponse response, GatewayErrorCode error)
      throws IOException {
    response.setStatus(error.status());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem(request, error));
  }

  public ProblemDetail problem(HttpServletRequest request, GatewayErrorCode error) {
    return new ProblemDetail(
        error.type(),
        error.title(),
        error.status(),
        error.name(),
        error.detail(),
        URI.create(request.getRequestURI()),
        correlationId());
  }

  private String correlationId() {
    Tracer tracer = tracerProvider.getIfAvailable();
    Span span = tracer == null ? null : tracer.currentSpan();
    return span == null ? UUID.randomUUID().toString() : span.context().traceId();
  }
}
