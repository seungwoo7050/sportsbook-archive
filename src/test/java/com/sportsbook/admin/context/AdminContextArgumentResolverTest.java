package com.sportsbook.admin.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.admin.security.AdminRole;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.ServletWebRequest;

class AdminContextArgumentResolverTest {

  private final AdminContextArgumentResolver resolver = new AdminContextArgumentResolver();

  @AfterEach
  void clearTraceContext() {
    MDC.clear();
  }

  @Test
  void createsAndCachesOneContextWithTheResponseActionHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setUserPrincipal(authentication("operator-1", "TRADER"));
    MDC.put("traceId", "trace-1");
    ServletWebRequest webRequest = new ServletWebRequest(request, response);

    AdminContext first = resolver.resolveArgument(parameter(), null, webRequest, null);
    AdminContext second = resolver.resolveArgument(parameter(), null, webRequest, null);

    assertThat(second).isSameAs(first);
    assertThat(first.actorId()).isEqualTo("operator-1");
    assertThat(first.actorRole()).isEqualTo(AdminRole.TRADER);
    assertThat(first.traceId()).isEqualTo("trace-1");
    assertThat(first.actionId().version()).isEqualTo(7);
    assertThat(response.getHeader(AdminContextArgumentResolver.ACTION_ID_HEADER))
        .isEqualTo(first.actionId().toString());
  }

  @Test
  void failsClosedWithoutAVerifiedJwt() throws Exception {
    ServletWebRequest webRequest =
        new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse());

    assertThatThrownBy(() -> resolver.resolveArgument(parameter(), null, webRequest, null))
        .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
  }

  private static MethodParameter parameter() throws NoSuchMethodException {
    Method method = Probe.class.getDeclaredMethod("handle", AdminContext.class);
    return new MethodParameter(method, 0);
  }

  private static JwtAuthenticationToken authentication(String subject, String role) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(subject)
            .claim("role", role)
            .build();
    return new JwtAuthenticationToken(
        jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)), subject);
  }

  private static final class Probe {
    void handle(AdminContext context) {}
  }
}
