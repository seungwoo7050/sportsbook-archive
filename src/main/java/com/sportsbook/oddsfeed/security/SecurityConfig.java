package com.sportsbook.oddsfeed.security;

import com.sportsbook.oddsfeed.config.InternalSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** Defines the complete HTTP exposure boundary. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalSecurityProperties.class)
public class SecurityConfig {

  @Bean
  public InternalApiKeyAuthenticationFilter internalApiKeyAuthenticationFilter(
      InternalSecurityProperties properties) {
    return new InternalApiKeyAuthenticationFilter(properties);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, InternalApiKeyAuthenticationFilter internalFilter) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/events",
                        "/api/v1/events/**",
                        "/api/v1/odds/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/internal/v1/events/*/markets/*/suspend")
                    .hasAuthority(InternalApiKeyAuthenticationFilter.AUTHORITY)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/events/*/markets/*/close")
                    .hasAuthority(InternalApiKeyAuthenticationFilter.AUTHORITY)
                    .requestMatchers(HttpMethod.POST, "/internal/v1/events/*/markets/*/reopen")
                    .hasAuthority(InternalApiKeyAuthenticationFilter.AUTHORITY)
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(internalFilter, AnonymousAuthenticationFilter.class)
        .build();
  }
}
