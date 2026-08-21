package com.sportsbook.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.wallet.web.WalletError;
import com.sportsbook.wallet.web.WalletProblems;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Emits fixed problem bodies without reflecting credentials or exception messages. */
public final class WalletSecurityFailureHandler
    implements AuthenticationEntryPoint, AccessDeniedHandler {
  static final String AUTHENTICATION_DETAIL = "Valid internal service credentials are required";
  static final String ACCESS_DETAIL = "Authenticated caller cannot access this wallet resource";

  private final ObjectMapper objectMapper;

  public WalletSecurityFailureHandler(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    authenticationRequired(request, response);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      org.springframework.security.access.AccessDeniedException exception)
      throws IOException, ServletException {
    write(request, response, WalletError.ACCESS_DENIED, ACCESS_DETAIL);
  }

  void authenticationRequired(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    write(request, response, WalletError.AUTHENTICATION_REQUIRED, AUTHENTICATION_DETAIL);
  }

  private void write(
      HttpServletRequest request, HttpServletResponse response, WalletError error, String detail)
      throws IOException {
    ProblemDetail problem = WalletProblems.from(error, detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(error.httpStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
