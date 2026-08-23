package com.sportsbook.admin.audit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sportsbook.admin.security.TestJwtKeys;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

final class AuditHttpTestEnvironment {

  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static final WireMockServer DOWNSTREAM =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  static {
    POSTGRES.start();
    DOWNSTREAM.start();
  }

  private AuditHttpTestEnvironment() {}

  static void register(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("admin.security.jwt.public-key", TestJwtKeys::publicKeyPem);
    registry.add("admin.downstream.wallet-base-url", AuditHttpTestEnvironment::baseUrl);
    registry.add("admin.downstream.risk-base-url", AuditHttpTestEnvironment::baseUrl);
    registry.add("admin.downstream.odds-feed-base-url", AuditHttpTestEnvironment::baseUrl);
    registry.add("admin.downstream.settlement-base-url", AuditHttpTestEnvironment::baseUrl);
  }

  static void stop() {
    DOWNSTREAM.stop();
    POSTGRES.stop();
  }

  private static String baseUrl() {
    return "http://127.0.0.1:" + DOWNSTREAM.port();
  }
}
