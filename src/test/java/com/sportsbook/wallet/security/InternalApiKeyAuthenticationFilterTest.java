package com.sportsbook.wallet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.wallet.domain.WalletCaller;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalApiKeyAuthenticationFilterTest {
  private final WalletCredentials credentials = new WalletCredentials(properties());
  private final WalletSecurityFailureHandler failureHandler =
      new WalletSecurityFailureHandler(
          new ObjectMapper().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class));
  private final InternalApiKeyAuthenticationFilter filter =
      new InternalApiKeyAuthenticationFilter(credentials, failureHandler);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void continuesRequestsThatDoNotPresentCredentials() throws Exception {
    AtomicBoolean invoked = new AtomicBoolean();
    AtomicReference<Authentication> observed = new AtomicReference<>();

    filter.doFilter(
        request(),
        new MockHttpServletResponse(),
        (ignoredRequest, ignoredResponse) -> {
          invoked.set(true);
          observed.set(SecurityContextHolder.getContext().getAuthentication());
        });

    assertThat(invoked).isTrue();
    assertThat(observed.get()).isNull();
  }

  @ParameterizedTest
  @EnumSource(WalletCaller.class)
  void authenticatesEachExactPairWithoutRetainingTheKey(WalletCaller caller) throws Exception {
    MockHttpServletRequest request = request();
    request.addHeader(InternalApiKeyAuthenticationFilter.SERVICE_HEADER, caller.wireName());
    request.addHeader(InternalApiKeyAuthenticationFilter.API_KEY_HEADER, key(caller));
    AtomicReference<Authentication> observed = new AtomicReference<>();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (ignoredRequest, ignoredResponse) ->
            observed.set(SecurityContextHolder.getContext().getAuthentication()));

    assertThat(observed.get().getPrincipal()).isEqualTo(caller);
    assertThat(observed.get().getCredentials()).isNull();
    assertThat(observed.get().getAuthorities()).isEmpty();
    assertThat(observed.get().isAuthenticated()).isTrue();
  }

  @Test
  void rejectsMissingDependencies() {
    assertThatNullPointerException()
        .isThrownBy(() -> new InternalApiKeyAuthenticationFilter(null, failureHandler));
    assertThatNullPointerException()
        .isThrownBy(() -> new InternalApiKeyAuthenticationFilter(credentials, null));
  }

  private MockHttpServletRequest request() {
    return new MockHttpServletRequest("GET", "/internal/v1/wallet/balance");
  }

  private static WalletSecurityProperties properties() {
    return new WalletSecurityProperties(
        key(WalletCaller.PLATFORM),
        key(WalletCaller.GATEWAY),
        key(WalletCaller.BETTING),
        key(WalletCaller.SETTLEMENT),
        key(WalletCaller.ADMIN));
  }

  private static String key(WalletCaller caller) {
    return caller.wireName() + ":" + caller.name().repeat(8);
  }
}
