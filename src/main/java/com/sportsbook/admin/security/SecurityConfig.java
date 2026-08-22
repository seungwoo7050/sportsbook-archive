package com.sportsbook.admin.security;

import com.sportsbook.admin.error.Rfc7807Writer;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

  @Bean
  SecurityFilterChain adminSecurityFilterChain(
      HttpSecurity http, Rfc7807Writer problems, AdminNetworkProperties networkProperties)
      throws Exception {
    IpAllowlistFilter ipAllowlist = new IpAllowlistFilter(networkProperties, problems);
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/actuator/prometheus",
                        "/error")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            failures ->
                failures
                    .authenticationEntryPoint(
                        (request, response, failure) ->
                            problems.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                Rfc7807Writer.UNAUTHORIZED,
                                "Unauthorized",
                                "UNAUTHORIZED",
                                "Authentication is required"))
                    .accessDeniedHandler(
                        (request, response, failure) ->
                            problems.write(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                Rfc7807Writer.FORBIDDEN,
                                "Forbidden",
                                "FORBIDDEN",
                                "The operator is not allowed to perform this action")))
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                    .authenticationEntryPoint(
                        (request, response, failure) ->
                            problems.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                Rfc7807Writer.UNAUTHORIZED,
                                "Unauthorized",
                                "UNAUTHORIZED",
                                "Authentication is required")))
        .addFilterBefore(ipAllowlist, BearerTokenAuthenticationFilter.class)
        .build();
  }

  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt ->
            AdminRole.fromClaim(jwt.getClaims().get("role"))
                .map(role -> List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())))
                .orElseGet(List::of));
    return converter;
  }
}
