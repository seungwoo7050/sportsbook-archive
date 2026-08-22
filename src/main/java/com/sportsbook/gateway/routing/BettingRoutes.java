package com.sportsbook.gateway.routing;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.POST;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@EnableConfigurationProperties(BettingDownstreamProperties.class)
public class BettingRoutes {

  private final BettingDownstreamProperties downstream;
  private final DownstreamRequestSanitizer sanitizer;
  private final IdentityForwarding identity;
  private final BettingRequestAuthentication authentication;
  private final TraceForwarding trace;
  private final DownstreamFailureBoundary failures;

  public BettingRoutes(
      BettingDownstreamProperties downstream,
      DownstreamRequestSanitizer sanitizer,
      IdentityForwarding identity,
      TraceForwarding trace,
      DownstreamFailureBoundary failures) {
    this.downstream = downstream;
    this.sanitizer = sanitizer;
    this.identity = identity;
    this.authentication = new BettingRequestAuthentication(downstream);
    this.trace = trace;
    this.failures = failures;
  }

  @Bean
  RouterFunction<ServerResponse> bettingRoute() {
    return route("betting")
        .route(
            POST("/api/v1/bets").or(GET("/api/v1/bets")).or(GET("/api/v1/bets/{betId}")),
            http(downstream.bettingUri().toString()))
        .before(sanitizer::apply)
        .before(identity::apply)
        .before(authentication::apply)
        .before(trace::apply)
        .before(this::rewrite)
        .filter(failures)
        .build();
  }

  private ServerRequest rewrite(ServerRequest request) {
    String path = request.uri().getRawPath().replaceFirst("^/api/v1/bets", "/internal/v1/bets");
    URI uri = UriComponentsBuilder.fromUri(request.uri()).replacePath(path).build(true).toUri();
    ServerRequest.Builder forwarded = ServerRequest.from(request).uri(uri);
    if (request.method() == HttpMethod.GET && path.equals("/internal/v1/bets")) {
      identity
          .currentSubject()
          .ifPresent(subject -> forwarded.params(params -> params.set("userId", subject)));
    }
    return forwarded.build();
  }
}
