package com.sportsbook.gateway.security;

import com.sportsbook.gateway.error.GatewayErrorCode;
import com.sportsbook.gateway.error.GatewayProblemWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, GatewayProblemWriter problems)
      throws Exception {
    AuthenticationEntryPoint unauthorized =
        (request, response, failure) ->
            problems.write(request, response, GatewayErrorCode.GATEWAY_UNAUTHORIZED);
    AccessDeniedHandler forbidden =
        (request, response, failure) ->
            problems.write(request, response, GatewayErrorCode.GATEWAY_FORBIDDEN);

    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            access ->
                access
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/events", "/api/v1/events/*", "/api/v1/odds/*/*/*")
                    .permitAll()
                    .requestMatchers("/ws/v1/odds", "/ws/v1/bets")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/bets")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/bets", "/api/v1/bets/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/wallet/balance")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            failures ->
                failures.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(unauthorized)
                    .jwt(
                        jwt ->
                            jwt.jwtAuthenticationConverter(
                                new GatewayJwtAuthenticationConverter())));
    return http.build();
  }
}
