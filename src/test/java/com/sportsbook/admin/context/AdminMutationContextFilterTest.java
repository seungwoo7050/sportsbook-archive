package com.sportsbook.admin.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AdminMutationContextFilterTest {

  private final AdminMutationContextFilter filter = new AdminMutationContextFilter();

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createsOneUuid7IdentityBeforeAControllerBindsTheMutation() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(authentication());
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/admin/v1/wallet/user-1/refund");
    request.setRequestURI("/admin/v1/wallet/user-1/refund");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    UUID actionId =
        UUID.fromString(response.getHeader(AdminContextArgumentResolver.ACTION_ID_HEADER));
    assertThat(actionId.version()).isEqualTo(7);
    assertThat(AdminContextArgumentResolver.initialize(request, response).actionId())
        .isEqualTo(actionId);
  }

  @Test
  void leavesReadsAndUnauthenticatedMutationsWithoutAnIdentity() throws Exception {
    MockHttpServletRequest read = new MockHttpServletRequest("GET", "/admin/v1/audit-logs");
    read.setRequestURI("/admin/v1/audit-logs");
    MockHttpServletResponse readResponse = new MockHttpServletResponse();
    filter.doFilter(read, readResponse, new MockFilterChain());

    MockHttpServletRequest mutation = new MockHttpServletRequest("POST", "/admin/v1/test");
    mutation.setRequestURI("/admin/v1/test");
    MockHttpServletResponse mutationResponse = new MockHttpServletResponse();
    filter.doFilter(mutation, mutationResponse, new MockFilterChain());

    assertThat(readResponse.getHeader(AdminContextArgumentResolver.ACTION_ID_HEADER)).isNull();
    assertThat(mutationResponse.getHeader(AdminContextArgumentResolver.ACTION_ID_HEADER)).isNull();
  }

  private static JwtAuthenticationToken authentication() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("operator-1")
            .claim("role", "ADMIN")
            .build();
    return new JwtAuthenticationToken(
        jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), "operator-1");
  }
}
