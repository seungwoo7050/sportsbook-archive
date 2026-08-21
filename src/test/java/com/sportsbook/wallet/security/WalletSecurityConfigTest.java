package com.sportsbook.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.wallet.domain.WalletCaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
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
  @Autowired private FilterChainProxy security;

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

  @Test
  void authenticatesPlatformManagementAndRejectsOtherCallers() throws Exception {
    mvc.perform(get("/actuator/info"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("WALLET_AUTHENTICATION_REQUIRED"));
    mvc.perform(internalGet("/actuator/info", WalletCaller.PLATFORM)).andExpect(status().isOk());
    mvc.perform(internalGet("/actuator/metrics", WalletCaller.PLATFORM)).andExpect(status().isOk());
    mvc.perform(internalGet("/actuator", WalletCaller.PLATFORM)).andExpect(status().isOk());
    mvc.perform(
            get("/actuator/info")
                .header(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, "platform")
                .header(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, "invalid"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get("/actuator/health")
                .header(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, "platform")
                .header(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, "invalid"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/actuator/health"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("WALLET_AUTHENTICATION_REQUIRED"));
    mvc.perform(internalGet("/internal/test", WalletCaller.PLATFORM))
        .andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @EnumSource(
      value = WalletCaller.class,
      names = {"GATEWAY", "BETTING", "SETTLEMENT", "ADMIN"})
  void rejectsNonPlatformManagementCallers(WalletCaller caller) throws Exception {
    mvc.perform(internalGet("/actuator/info", caller))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("WALLET_ACCESS_DENIED"));
  }

  @Test
  void registersTheInternalFilterOnlyInTheSecurityChain() {
    var filters = security.getFilters("/actuator/info");
    assertThat(filters.stream().filter(InternalApiKeyAuthenticationFilter.class::isInstance))
        .hasSize(1);
    assertThat(filters)
        .extracting(Object::getClass)
        .containsSubsequence(
            SecurityContextHolderFilter.class,
            InternalApiKeyAuthenticationFilter.class,
            AnonymousAuthenticationFilter.class);
    assertThat(beans.getBeanProvider(InternalApiKeyAuthenticationFilter.class).stream()).isEmpty();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder internalGet(
      String path, WalletCaller caller) {
    return get(path)
        .header(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, caller.wireName())
        .header(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, TestInternalApiKeys.key(caller));
  }

  @RestController
  static class TestEndpoints {
    @GetMapping({
      "/actuator",
      "/actuator/health",
      "/actuator/health/liveness",
      "/actuator/prometheus",
      "/actuator/info",
      "/actuator/metrics",
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
