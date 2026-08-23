package com.sportsbook.admin.context;

import com.sportsbook.admin.security.AdminRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public final class AdminContextArgumentResolver implements HandlerMethodArgumentResolver {

  public static final String ACTION_ID_HEADER = "X-Admin-Action-Id";
  private static final String REQUEST_ATTRIBUTE = AdminContext.class.getName();

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.getParameterType() == AdminContext.class;
  }

  @Override
  public AdminContext resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer container,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
    HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
    if (request == null || response == null) {
      throw new AuthenticationCredentialsNotFoundException("HTTP request context is required");
    }
    return initialize(request, response);
  }

  static AdminContext initialize(HttpServletRequest request, HttpServletResponse response) {
    Object cached = request.getAttribute(REQUEST_ATTRIBUTE);
    if (cached instanceof AdminContext context) {
      return context;
    }
    Object principal = request.getUserPrincipal();
    Object securityAuthentication = SecurityContextHolder.getContext().getAuthentication();
    JwtAuthenticationToken authentication =
        principal instanceof JwtAuthenticationToken jwt
            ? jwt
            : securityAuthentication instanceof JwtAuthenticationToken jwt ? jwt : null;
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new AuthenticationCredentialsNotFoundException("Verified operator JWT is required");
    }
    AdminRole role =
        AdminRole.fromClaim(authentication.getToken().getClaims().get("role"))
            .orElseThrow(
                () -> new AuthenticationCredentialsNotFoundException("Verified role is required"));
    AdminContext context =
        new AdminContext(authentication.getName(), role, Uuid7.generate(), MDC.get("traceId"));
    request.setAttribute(REQUEST_ATTRIBUTE, context);
    response.setHeader(ACTION_ID_HEADER, context.actionId().toString());
    return context;
  }
}
