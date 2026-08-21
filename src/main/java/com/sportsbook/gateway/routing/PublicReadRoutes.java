package com.sportsbook.gateway.routing;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.GET;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
@EnableConfigurationProperties(OddsFeedDownstreamProperties.class)
public class PublicReadRoutes {

  private final OddsFeedDownstreamProperties downstream;
  private final DownstreamRequestSanitizer sanitizer;
  private final TraceForwarding trace;
  private final DownstreamFailureBoundary failures;

  public PublicReadRoutes(
      OddsFeedDownstreamProperties downstream,
      DownstreamRequestSanitizer sanitizer,
      TraceForwarding trace,
      DownstreamFailureBoundary failures) {
    this.downstream = downstream;
    this.sanitizer = sanitizer;
    this.trace = trace;
    this.failures = failures;
  }

  @Bean
  RouterFunction<ServerResponse> publicReadRoute() {
    return route("public-reads")
        .route(
            GET("/api/v1/events")
                .or(GET("/api/v1/events/{eventId}"))
                .or(GET("/api/v1/odds/{eventId}/{marketId}/{selectionId}")),
            http(downstream.oddsFeedUri().toString()))
        .before(sanitizer::apply)
        .before(trace::apply)
        .filter(failures)
        .build();
  }
}
