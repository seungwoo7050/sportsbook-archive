package com.sportsbook.wallet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.wallet.domain.WalletCaller;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
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
                    .access(callers(WalletCaller.PLATFORM))
                    .requestMatchers(HttpMethod.POST, "/internal/v1/wallet/accounts")
                    .access(callers(WalletCaller.PLATFORM))
                    .requestMatchers(HttpMethod.GET, "/internal/v1/wallet/accounts/*/balance")
                    .access(callers(WalletCaller.PLATFORM, WalletCaller.GATEWAY))
                    .requestMatchers(
                        HttpMethod.POST,
                        "/internal/v1/wallet/transactions/deposit",
                        "/internal/v1/wallet/transactions/withdraw")
                    .access(callers(WalletCaller.PLATFORM))
                    .requestMatchers(HttpMethod.POST, "/internal/v1/wallet/transactions/debit")
                    .access(callers(WalletCaller.BETTING))
                    .requestMatchers(HttpMethod.GET, "/internal/v1/wallet/transactions/debit/*")
                    .access(callers(WalletCaller.BETTING))
                    .requestMatchers(HttpMethod.POST, "/internal/v1/wallet/transactions/credit")
                    .access(
                        callers(WalletCaller.BETTING, WalletCaller.SETTLEMENT, WalletCaller.ADMIN))
                    .requestMatchers(
                        HttpMethod.POST,
                        "/internal/v1/wallet/transactions/forfeit",
                        "/internal/v1/wallet/transactions/adjustment")
                    .access(callers(WalletCaller.SETTLEMENT))
                    .requestMatchers(
                        HttpMethod.GET, "/internal/v1/wallet/transactions/adjustment/*")
                    .access(callers(WalletCaller.SETTLEMENT))
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

  private static AuthorizationManager<RequestAuthorizationContext> callers(
      WalletCaller... allowedCallers) {
    Set<WalletCaller> allowed = Set.of(allowedCallers);
    return (authentication, context) ->
        new AuthorizationDecision(allowed.contains(authentication.get().getPrincipal()));
  }

  @Bean
  AuthenticationManager rejectingAuthenticationManager() {
    return authentication -> {
      throw new UnsupportedOperationException("No configured wallet authentication mechanism");
    };
  }
}
