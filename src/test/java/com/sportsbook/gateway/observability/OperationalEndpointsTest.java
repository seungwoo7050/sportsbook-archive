package com.sportsbook.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@AutoConfigureObservability(metrics = true, tracing = false)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gateway.ratelimit.enabled=false",
      "gateway.downstream.wallet.api-key=fixture-wallet-key-32-characters-long"
    })
class OperationalEndpointsTest {

  @Autowired TestRestTemplate http;
  @Autowired WebEndpointsSupplier endpoints;
  @Autowired HealthEndpointGroups healthGroups;
  @Autowired BuildProperties buildProperties;
  @MockBean JwtDecoder decoder;

  @Test
  void exposesOnlyThePublicOperationalInventory() {
    Set<String> exposed =
        endpoints.getEndpoints().stream()
            .map(ExposableWebEndpoint::getEndpointId)
            .map(Object::toString)
            .collect(Collectors.toSet());

    assertThat(exposed).containsExactlyInAnyOrder("health", "info", "prometheus");
  }

  @Test
  void reportsOnlyAvailabilityStateForProbes() {
    for (String group : new String[] {"liveness", "readiness"}) {
      ResponseEntity<JsonNode> response =
          http.getForEntity("/actuator/health/" + group, JsonNode.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
      assertThat(response.getBody().fieldNames()).toIterable().containsExactly("status");
    }
    assertThat(healthGroups.get("liveness").isMember("livenessState")).isTrue();
    assertThat(healthGroups.get("liveness").isMember("readinessState")).isFalse();
    assertThat(healthGroups.get("readiness").isMember("readinessState")).isTrue();
    assertThat(healthGroups.get("readiness").isMember("livenessState")).isFalse();
    for (String dependency : new String[] {"redis", "kafka", "betting", "wallet", "oddsFeed"}) {
      assertThat(healthGroups.get("liveness").isMember(dependency)).isFalse();
      assertThat(healthGroups.get("readiness").isMember(dependency)).isFalse();
    }
  }

  @Test
  void publishesBuildIdentityAndPrometheusMetricsWithoutSourceMetadata() {
    ResponseEntity<JsonNode> info = http.getForEntity("/actuator/info", JsonNode.class);
    JsonNode build = info.getBody().path("build");
    assertThat(build.path("group").asText()).isEqualTo("com.sportsbook");
    assertThat(build.path("artifact").asText()).isEqualTo("gateway");
    assertThat(build.path("version").asText()).isEqualTo(buildProperties.getVersion());
    assertThat(info.getBody().has("git")).isFalse();

    ResponseEntity<String> metrics = http.getForEntity("/actuator/prometheus", String.class);
    assertThat(metrics.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(metrics.getBody())
        .containsPattern("gateway_ratelimit_fail_open_total\\{service=\"gateway\",?} 0\\.0");
  }
}
