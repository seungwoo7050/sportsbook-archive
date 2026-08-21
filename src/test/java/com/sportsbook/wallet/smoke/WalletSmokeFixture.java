package com.sportsbook.wallet.smoke;

import com.sportsbook.wallet.domain.WalletCaller;
import com.sportsbook.wallet.security.TestInternalApiKeys;
import com.sportsbook.wallet.web.WalletRequestHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class WalletSmokeFixture {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

  @Autowired TestRestTemplate http;

  @DynamicPropertySource
  static void runtimeProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("wallet.outbox.scheduling-enabled", () -> "false");
    registry.add("wallet.recovery.scheduling-enabled", () -> "false");
    registry.add("wallet.integrity.scheduling-enabled", () -> "false");
    TestInternalApiKeys.register(registry);
  }

  ResponseEntity<String> request(
      HttpMethod method, String path, WalletCaller caller, String operationKey, String body) {
    HttpHeaders headers = new HttpHeaders();
    if (caller != null) {
      headers.set("X-Internal-Service", caller.wireName());
      headers.set("X-Internal-Api-Key", TestInternalApiKeys.key(caller));
    }
    if (operationKey != null) {
      headers.set(WalletRequestHeaders.IDEMPOTENCY_KEY, operationKey);
    }
    if (body != null) {
      headers.setContentType(MediaType.APPLICATION_JSON);
    }
    return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
  }
}
