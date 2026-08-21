package com.sportsbook.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = WalletSecurityConfigTest.TestEndpoints.class)
@Import({WalletSecurityConfig.class, WalletSecurityConfigTest.TestEndpoints.class})
class WalletSecurityConfigTest {

  @Autowired private MockMvc mvc;
  @Autowired private ListableBeanFactory beans;
  @Autowired private AuthenticationManager authenticationManager;

  @DynamicPropertySource
  static void securityProperties(DynamicPropertyRegistry registry) {
    TestInternalApiKeys.register(registry);
  }

  @Test
  void allowsOnlyAnonymousManagementProbes() throws Exception {
    MvcResult health = mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());

    mvc.perform(get("/actuator/info")).andExpect(status().is4xxClientError());
    mvc.perform(get("/actuator/metrics")).andExpect(status().is4xxClientError());
    mvc.perform(get("/internal/test")).andExpect(status().is4xxClientError());
    mvc.perform(post("/actuator/health")).andExpect(status().is4xxClientError());
    assertThat(health.getRequest().getSession(false)).isNull();
  }

  @Test
  void createsNoGeneratedUserStore() {
    assertThat(beans.getBeanProvider(UserDetailsService.class).stream()).isEmpty();
    assertThatThrownBy(
            () ->
                authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("caller", "secret")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @RestController
  static class TestEndpoints {
    @GetMapping({
      "/actuator/health",
      "/actuator/health/liveness",
      "/actuator/prometheus",
      "/actuator/info",
      "/internal/test"
    })
    String getEndpoint() {
      return "ok";
    }

    @PostMapping("/actuator/health")
    String postHealth() {
      return "ok";
    }
  }
}
