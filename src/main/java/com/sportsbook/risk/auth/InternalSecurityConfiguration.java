package com.sportsbook.risk.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.error.ProblemDetail;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/** Restricts every internal operation to its owning service principal. */
@Configuration
public class InternalSecurityConfiguration {
  @Bean
  SecurityFilterChain internalSecurity(
      HttpSecurity http, InternalAuthenticationFilter authentication, ObjectMapper mapper)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        "/actuator/health", "/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/v1/risk/reservations")
                    .hasRole("BETTING_SERVICE")
                    .requestMatchers(HttpMethod.PUT, "/internal/v1/risk/reservations/*/commit")
                    .hasRole("BETTING_SERVICE")
                    .requestMatchers(HttpMethod.DELETE, "/internal/v1/risk/reservations/*")
                    .hasRole("BETTING_SERVICE")
                    .requestMatchers("/internal/v1/risk/limits/**")
                    .hasRole("ADMIN_API")
                    .requestMatchers(HttpMethod.POST, "/internal/v1/risk/check")
                    .hasRole("PLATFORM")
                    .requestMatchers("/actuator/**")
                    .hasRole("PLATFORM")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors.accessDeniedHandler(
                    (request, response, denied) ->
                        forbidden(request.getRequestURI(), response, mapper)))
        .addFilterBefore(authentication, AuthorizationFilter.class)
        .build();
  }

  private static void forbidden(String path, HttpServletResponse response, ObjectMapper mapper)
      throws IOException {
    ProblemDetail problem =
        new ProblemDetail(
            URI.create("https://sportsbook/errors/forbidden"),
            "Forbidden",
            HttpServletResponse.SC_FORBIDDEN,
            "FORBIDDEN",
            "The authenticated caller does not own this operation",
            URI.create(path),
            null);
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), problem);
  }
}
