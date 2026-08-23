package com.sportsbook.admin.audit;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.sportsbook.admin.security.TestJwtKeys;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.kafka.bootstrap-servers=127.0.0.1:1",
      "admin.audit.stale-scan-interval=PT1H",
      "admin.downstream.read-timeout=250ms",
      "admin.downstream.credentials.wallet-api-key=wallet-admin-http-key-000000000001",
      "admin.downstream.credentials.risk-api-key=risk-admin-http-key-00000000000002",
      "admin.downstream.credentials.odds-feed-api-key=odds-admin-http-key-00000000000003",
      "admin.downstream.credentials.settlement-api-key=settlement-admin-http-key-000000004"
    })
@AutoConfigureMockMvc
@AutoConfigureObservability
class AuditHttpIntegrationTest {

  private static final UUID EVENT_ID = UUID.fromString("3f9b0ba6-558f-4df1-a31c-835f3cd57f9d");
  private static final UUID MARKET_ID = UUID.fromString("9f50e81c-327a-461e-91cd-0596a0d22865");
  private static final String PATH =
      "/internal/v1/events/" + EVENT_ID + "/markets/" + MARKET_ID + "/suspend";

  @Autowired private MockMvc mvc;
  @Autowired private AuditLogRepository auditLogs;
  @MockBean private AdminActionPublisher publisher;

  @DynamicPropertySource
  static void dependencies(DynamicPropertyRegistry registry) {
    AuditHttpTestEnvironment.register(registry);
  }

  @BeforeEach
  void reset() {
    auditLogs.deleteAll();
    AuditHttpTestEnvironment.DOWNSTREAM.resetAll();
  }

  @AfterAll
  static void stopDependencies() {
    AuditHttpTestEnvironment.stop();
  }

  private ResultActions suspend() throws Exception {
    return mvc.perform(
        post("/admin/v1/events/{eventId}/markets/{marketId}/suspend", EVENT_ID, MARKET_ID)
            .header(AUTHORIZATION, TestJwtKeys.bearer("operator-17", "TRADER"))
            .header("Idempotency-Key", "e292ac36-1c66-4c17-9027-d6aa63df1ae9")
            .contentType(APPLICATION_JSON)
            .content("{\"reason\":\"feed investigation\"}"));
  }

  private void stubOdds(int status, String body, int delayMillis) {
    ResponseDefinitionBuilder response = WireMock.aResponse().withStatus(status);
    if (body != null) {
      response.withHeader("Content-Type", "application/problem+json").withBody(body);
    }
    if (delayMillis > 0) {
      response.withFixedDelay(delayMillis);
    }
    AuditHttpTestEnvironment.DOWNSTREAM.stubFor(
        WireMock.post(WireMock.urlEqualTo(PATH)).willReturn(response));
  }
}
