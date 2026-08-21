package com.sportsbook.wallet.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Establishes the closed HTTP boundary before monetary routes are exposed. */
@Configuration
public class WalletSecurityConfig {

  @Bean
  SecurityFilterChain walletSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/prometheus")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }

  @Bean
  AuthenticationManager rejectingAuthenticationManager() {
    return authentication -> {
      throw new UnsupportedOperationException("No configured wallet authentication mechanism");
    };
  }
}
