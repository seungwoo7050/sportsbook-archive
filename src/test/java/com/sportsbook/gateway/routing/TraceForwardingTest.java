package com.sportsbook.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

class TraceForwardingTest {

  private static final String SAMPLED = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
  private static final String UNSAMPLED = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00";

  @Test
  void preservesSingleValidSampledAndUnsampledValues() {
    TraceForwarding forwarding = new TraceForwarding(provider(null));

    for (String traceparent : List.of(SAMPLED, UNSAMPLED)) {
      assertThat(forwarding.apply(request(traceparent)).headers().firstHeader("traceparent"))
          .isEqualTo(traceparent);
    }
  }

  @Test
  void removesMalformedAndAllZeroValuesWithoutAnActiveSpan() {
    TraceForwarding forwarding = new TraceForwarding(provider(null));
    List<String> invalid =
        List.of(
            "00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01",
            "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",
            "invalid");

    for (String traceparent : invalid) {
      assertThat(forwarding.apply(request(traceparent)).headers().header("traceparent")).isEmpty();
    }
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.addHeader("traceparent", SAMPLED);
    servletRequest.addHeader("traceparent", UNSAMPLED);
    ServerRequest result = forwarding.apply(ServerRequest.create(servletRequest, List.of()));
    assertThat(result.headers().header("traceparent")).isEmpty();
  }

  @Test
  void createsAValidValueFromTheActiveSpan() {
    TraceContext context = mock(TraceContext.class);
    when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
    when(context.spanId()).thenReturn("00f067aa0ba902b7");
    when(context.sampled()).thenReturn(true);
    Span span = mock(Span.class);
    when(span.context()).thenReturn(context);
    Tracer tracer = mock(Tracer.class);
    when(tracer.currentSpan()).thenReturn(span);

    ServerRequest result = new TraceForwarding(provider(tracer)).apply(request("invalid"));

    assertThat(result.headers().firstHeader("traceparent")).isEqualTo(SAMPLED);
  }

  private static ServerRequest request(String traceparent) {
    MockHttpServletRequest servletRequest = new MockHttpServletRequest();
    servletRequest.addHeader("traceparent", traceparent);
    return ServerRequest.create(servletRequest, List.of());
  }

  @SuppressWarnings("unchecked")
  private static ObjectProvider<Tracer> provider(Tracer tracer) {
    ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(tracer);
    return provider;
  }
}
