package com.sportsbook.gateway.routing;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.GET;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(WalletDownstreamProperties.class)
public class WalletRoutes {

  private final WalletDownstreamProperties downstream;
  private final DownstreamRequestSanitizer sanitizer;
  private final IdentityForwarding identity;
  private final WalletRequestAuthentication authentication;
  private final TraceForwarding trace;
  private final DownstreamFailureBoundary failures;

  public WalletRoutes(
      WalletDownstreamProperties downstream,
      DownstreamRequestSanitizer sanitizer,
      IdentityForwarding identity,
      TraceForwarding trace,
      DownstreamFailureBoundary failures) {
    this.downstream = downstream;
    this.sanitizer = sanitizer;
    this.identity = identity;
    this.authentication = new WalletRequestAuthentication(downstream);
    this.trace = trace;
    this.failures = failures;
  }

  @Bean
  RouterFunction<ServerResponse> walletBalanceRoute() {
    return route("wallet-balance")
        .route(GET("/api/v1/wallet/balance"), http(downstream.uri().toString()))
        .before(sanitizer::apply)
        .before(identity::apply)
        .before(authentication::apply)
        .before(trace::apply)
        .before(this::rewrite)
        .filter(failures)
        .build();
  }

  private ServerRequest rewrite(ServerRequest request) {
    String subject = identity.currentSubject().orElseThrow();
    URI uri =
        UriComponentsBuilder.fromUri(request.uri())
            .replacePath("/internal/v1/wallet/accounts/" + subject + "/balance")
            .build(true)
            .toUri();
    return ServerRequest.from(request).uri(uri).build();
  }
}
