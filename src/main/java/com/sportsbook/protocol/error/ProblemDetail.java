package com.sportsbook.protocol.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.net.URI;
import java.util.Objects;

/**
 * RFC 7807 ProblemDetail with two sportsbook-specific extensions: {@code errorCode} (stable
 * machine-readable identifier — the {@link ErrorCode} enum name) and {@code correlationId}
 * (Micrometer/OTel trace id).
 *
 * <p>Self-defined rather than reusing {@code org.springframework.http.ProblemDetail} so this
 * library stays framework-neutral. Spring's class pulls in spring-web transitively which is
 * unwanted for non-web consumers (background workers, batch jobs).
 *
 * <p>{@code @JsonInclude(NON_NULL)} keeps the wire compact when optional fields are absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetail(
    URI type,
    String title,
    int status,
    String errorCode,
    String detail,
    URI instance,
    String correlationId) {

  public ProblemDetail {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(errorCode, "errorCode");
  }
}
