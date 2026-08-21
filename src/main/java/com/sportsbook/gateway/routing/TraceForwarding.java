package com.sportsbook.gateway.routing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

@Component
public class TraceForwarding {

  private static final String TRACEPARENT = "traceparent";
  private static final Pattern VALID_TRACEPARENT =
      Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-(00|01)");
  private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
  private static final String ZERO_SPAN_ID = "0000000000000000";

  private final ObjectProvider<Tracer> tracer;

  public TraceForwarding(ObjectProvider<Tracer> tracer) {
    this.tracer = tracer;
  }

  public ServerRequest apply(ServerRequest request) {
    List<String> inbound = request.headers().header(TRACEPARENT);
    if (inbound.size() == 1 && isValid(inbound.get(0))) {
      return request;
    }
    ServerRequest sanitized = request;
    if (!inbound.isEmpty()) {
      sanitized =
          ServerRequest.from(request).headers(headers -> headers.remove(TRACEPARENT)).build();
    }
    String traceparent = currentTraceparent();
    if (!isValid(traceparent)) {
      return sanitized;
    }
    return ServerRequest.from(sanitized).header(TRACEPARENT, traceparent).build();
  }

  private boolean isValid(String value) {
    if (value == null || !VALID_TRACEPARENT.matcher(value).matches()) {
      return false;
    }
    return !value.regionMatches(3, ZERO_TRACE_ID, 0, ZERO_TRACE_ID.length())
        && !value.regionMatches(36, ZERO_SPAN_ID, 0, ZERO_SPAN_ID.length());
  }

  private String currentTraceparent() {
    Tracer activeTracer = tracer.getIfAvailable();
    if (activeTracer == null) {
      return null;
    }
    Span span = activeTracer.currentSpan();
    if (span == null) {
      return null;
    }
    TraceContext context = span.context();
    String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
    return "00-" + context.traceId() + "-" + context.spanId() + "-" + flags;
  }
}
