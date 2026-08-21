package com.sportsbook.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.wallet.domain.WalletCaller;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

  @ParameterizedTest
  @MethodSource("walletRoutes")
  void enforcesEveryWalletRouteCapability(WalletRoute route) throws Exception {
    for (WalletCaller caller : WalletCaller.values()) {
      int expected = route.allowed().contains(caller) ? 200 : 403;
      mvc.perform(internalRequest(route.method(), route.path(), caller))
          .andExpect(status().is(expected));
    }
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder internalGet(
      String path, WalletCaller caller) {
    return internalRequest(HttpMethod.GET, path, caller);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      internalRequest(HttpMethod method, String path, WalletCaller caller) {
    return request(method, path)
        .header(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, caller.wireName())
        .header(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, TestInternalApiKeys.key(caller));
  }

  static Stream<Arguments> walletRoutes() {
    return Stream.of(
        route(HttpMethod.POST, "/internal/v1/wallet/accounts", WalletCaller.PLATFORM),
        route(
            HttpMethod.GET,
            "/internal/v1/wallet/accounts/user/balance",
            WalletCaller.PLATFORM,
            WalletCaller.GATEWAY),
        route(HttpMethod.POST, "/internal/v1/wallet/transactions/deposit", WalletCaller.PLATFORM),
        route(HttpMethod.POST, "/internal/v1/wallet/transactions/withdraw", WalletCaller.PLATFORM),
        route(HttpMethod.POST, "/internal/v1/wallet/transactions/debit", WalletCaller.BETTING),
        route(HttpMethod.GET, "/internal/v1/wallet/transactions/debit/bet", WalletCaller.BETTING),
        route(
            HttpMethod.POST,
            "/internal/v1/wallet/transactions/credit",
            WalletCaller.BETTING,
            WalletCaller.SETTLEMENT,
            WalletCaller.ADMIN),
        route(HttpMethod.POST, "/internal/v1/wallet/transactions/forfeit", WalletCaller.SETTLEMENT),
        route(
            HttpMethod.POST,
            "/internal/v1/wallet/transactions/adjustment",
            WalletCaller.SETTLEMENT),
        route(
            HttpMethod.GET,
            "/internal/v1/wallet/transactions/adjustment/revision",
            WalletCaller.SETTLEMENT));
  }

  private static Arguments route(HttpMethod method, String path, WalletCaller... allowed) {
    return Arguments.of(new WalletRoute(method, path, Set.of(allowed)));
  }

  private record WalletRoute(HttpMethod method, String path, Set<WalletCaller> allowed) {}

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

    @RequestMapping(
        path = {
          "/internal/v1/wallet/accounts",
          "/internal/v1/wallet/accounts/{userId}/balance",
          "/internal/v1/wallet/transactions/deposit",
          "/internal/v1/wallet/transactions/withdraw",
          "/internal/v1/wallet/transactions/debit",
          "/internal/v1/wallet/transactions/debit/{betId}",
          "/internal/v1/wallet/transactions/credit",
          "/internal/v1/wallet/transactions/forfeit",
          "/internal/v1/wallet/transactions/adjustment",
          "/internal/v1/wallet/transactions/adjustment/{revisionId}"
        },
        method = {RequestMethod.GET, RequestMethod.POST})
    String walletEndpoint() {
      return "ok";
    }

    @PostMapping("/actuator/health")
    String postHealth() {
      return "ok";
    }
  }
}
