package com.sportsbook.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.wallet.domain.WalletCaller;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** Establishes the closed HTTP boundary before monetary routes are exposed. */
@Configuration
@EnableConfigurationProperties(WalletSecurityProperties.class)
public class WalletSecurityConfig {

  @Bean
  SecurityFilterChain walletSecurityFilterChain(
      HttpSecurity http, WalletCredentials credentials, WalletSecurityFailureHandler failures)
      throws Exception {
    InternalApiKeyAuthenticationFilter authentication =
        new InternalApiKeyAuthenticationFilter(credentials, failures);
    return http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(failures).accessDeniedHandler(failures))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/actuator", "/actuator/**")
                    .access(
                        (authenticated, context) ->
                            new AuthorizationDecision(
                                WalletCaller.PLATFORM.equals(authenticated.get().getPrincipal())))
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(authentication, AnonymousAuthenticationFilter.class)
        .build();
  }

  @Bean
  WalletCredentials walletCredentials(WalletSecurityProperties properties) {
    return new WalletCredentials(properties);
  }

  @Bean
  WalletSecurityFailureHandler walletSecurityFailureHandler(ObjectMapper objectMapper) {
    return new WalletSecurityFailureHandler(objectMapper);
  }

  @Bean
  AuthenticationManager rejectingAuthenticationManager() {
    return authentication -> {
      throw new UnsupportedOperationException("No configured wallet authentication mechanism");
    };
  }
}
