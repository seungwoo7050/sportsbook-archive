package com.sportsbook.gateway.routing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RouterFunctions.route;

import com.sportsbook.gateway.error.GatewayProblemWriter;
import com.sportsbook.gateway.security.SecurityConfig;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@WebMvcTest
@Import({SecurityConfig.class, GatewayProblemWriter.class, DownstreamFailureBoundary.class})
class DownstreamFailureBoundaryTest {

  @Autowired MockMvc mvc;
  @MockBean JwtDecoder jwtDecoder;

  @TestConfiguration(proxyBeanMethods = false)
  static class FailureRoutes {

    @Bean
    RouterFunction<ServerResponse> failureRoute(DownstreamFailureBoundary failures) {
      return route(
              GET("/api/v1/events/{failure}"),
              request -> {
                if ("timeout".equals(request.pathVariable("failure"))) {
                  throw new ResourceAccessException(
                      "timed out", new IOException(new TimeoutException()));
                }
                throw new ResourceAccessException("unavailable", new ConnectException());
              })
          .filter(failures);
    }
  }

  @Test
  void returnsPublicGatewayTimeoutWithoutReauthentication() throws Exception {
    mvc.perform(get("/api/v1/events/timeout"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("GATEWAY_TIMEOUT"));
  }

  @Test
  void returnsPublicBadGatewayWithoutReauthentication() throws Exception {
    mvc.perform(get("/api/v1/events/unavailable"))
        .andExpect(status().isBadGateway())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("GATEWAY_BAD_GATEWAY"));
  }
}
