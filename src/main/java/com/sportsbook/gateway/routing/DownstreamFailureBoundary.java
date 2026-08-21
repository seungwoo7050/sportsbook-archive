package com.sportsbook.gateway.routing;

import com.sportsbook.gateway.error.GatewayErrorCode;
import com.sportsbook.gateway.error.GatewayProblemWriter;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public final class DownstreamFailureBoundary
    implements HandlerFilterFunction<ServerResponse, ServerResponse> {

  private final GatewayProblemWriter problems;

  public DownstreamFailureBoundary(GatewayProblemWriter problems) {
    this.problems = problems;
  }

  @Override
  public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> downstream)
      throws Exception {
    try {
      return downstream.handle(request);
    } catch (ResourceAccessException failure) {
      GatewayErrorCode error =
          hasCause(
                  failure,
                  SocketTimeoutException.class,
                  HttpTimeoutException.class,
                  TimeoutException.class)
              ? GatewayErrorCode.GATEWAY_TIMEOUT
              : GatewayErrorCode.GATEWAY_BAD_GATEWAY;
      return ServerResponse.status(error.status())
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(problems.problem(request.servletRequest(), error));
    }
  }

  @SafeVarargs
  private static boolean hasCause(Throwable failure, Class<? extends Throwable>... causeTypes) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      for (Class<? extends Throwable> causeType : causeTypes) {
        if (causeType.isInstance(cause)) {
          return true;
        }
      }
    }
    return false;
  }
}
