package com.sportsbook.admin.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.error.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public final class Rfc7807Writer {

  public static final URI UNAUTHORIZED = URI.create("https://sportsbook/errors/unauthorized");
  public static final URI FORBIDDEN = URI.create("https://sportsbook/errors/forbidden");
  public static final URI IP_NOT_ALLOWED = URI.create("https://sportsbook/errors/ip-not-allowed");

  private final ObjectMapper objectMapper;

  public Rfc7807Writer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      HttpStatus status,
      URI type,
      String title,
      String code,
      String detail)
      throws IOException {
    ProblemDetail body =
        new ProblemDetail(
            type,
            title,
            status.value(),
            code,
            detail,
            URI.create(request.getRequestURI()),
            MDC.get("traceId"));
    response.setStatus(status.value());
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setHeader("Cache-Control", "no-store");
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
