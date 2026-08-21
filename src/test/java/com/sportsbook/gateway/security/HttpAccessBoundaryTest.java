package com.sportsbook.gateway.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.gateway.error.GatewayProblemWriter;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import({SecurityConfig.class, GatewayProblemWriter.class})
class HttpAccessBoundaryTest {

  @Autowired MockMvc mvc;
  @MockBean JwtDecoder jwtDecoder;

  @Test
  void publicReadsAndWebsocketHandshakesDoNotRequireAuthentication() throws Exception {
    mvc.perform(get("/api/v1/events/fixture")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/odds/event/market/selection")).andExpect(status().isNotFound());
    mvc.perform(get("/ws/v1/odds")).andExpect(status().isNotFound());
  }

  @Test
  void privateEndpointsRequireAuthentication() throws Exception {
    mvc.perform(get("/api/v1/wallet/balance"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("GATEWAY_UNAUTHORIZED"));
    mvc.perform(post("/api/v1/bets").with(jwt())).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/bets/00000000-0000-0000-0000-000000000001").with(jwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void unexpectedMethodsAndPathsAreDenied() throws Exception {
    mvc.perform(post("/api/v1/events").with(jwt()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("GATEWAY_FORBIDDEN"));
    mvc.perform(get("/api/v1/bets/internal/hidden").with(jwt())).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/odds/incomplete").with(jwt())).andExpect(status().isForbidden());
    mvc.perform(get("/internal/health").with(jwt())).andExpect(status().isForbidden());
  }

  @Test
  void errorDispatchIsNotReauthenticated() throws Exception {
    mvc.perform(
            get("/internal/health")
                .with(
                    request -> {
                      request.setDispatcherType(DispatcherType.ERROR);
                      return request;
                    }))
        .andExpect(status().isNotFound());
  }
}
