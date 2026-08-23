package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.sportsbook.admin.security.TestJwtKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(
    properties = {
      "spring.kafka.bootstrap-servers=127.0.0.1:1",
      "admin.audit.stale-scan-interval=PT1H",
      "admin.downstream.read-timeout=750ms",
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

  @Test
  void exposesStartedBeforeCompletingAnExactSuccess() throws Exception {
    stubOdds(202, null, 400);
    CompletableFuture<ResultActions> request =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return suspend();
              } catch (Exception failure) {
                throw new CompletionException(failure);
              }
            });

    AuditLogEntity started = awaitStarted();
    assertThat(started.getActorId()).isEqualTo("operator-17");
    assertThat(started.getActorRole()).isEqualTo(com.sportsbook.admin.security.AdminRole.TRADER);
    assertThat(started.getAction()).isEqualTo("MARKET_SUSPEND");
    assertThat(started.getTarget()).isEqualTo(EVENT_ID + "/" + MARKET_ID);
    assertThat(started.getReason()).isEqualTo("feed investigation");
    assertThat(started.getTraceId()).isNotBlank();
    assertThat(started.getHttpStatus()).isNull();
    assertThat(started.getCompletedAt()).isNull();

    ResultActions response = request.get(3, TimeUnit.SECONDS);
    String actionHeader =
        response
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getHeader("X-Admin-Action-Id");
    assertThat(actionHeader).isEqualTo(started.getActionId().toString());
    AuditLogEntity terminal = auditLogs.findById(started.getActionId()).orElseThrow();
    assertThat(terminal.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    assertThat(terminal.getHttpStatus()).isEqualTo(202);
    assertThat(terminal.getCompletedAt()).isAfterOrEqualTo(terminal.getStartedAt());
  }

  private AuditLogEntity awaitStarted() throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
    while (Instant.now().isBefore(deadline)) {
      var rows = auditLogs.findAll();
      if (rows.size() == 1 && rows.get(0).getOutcome() == AuditOutcome.STARTED) {
        return rows.get(0);
      }
      Thread.sleep(20);
    }
    throw new AssertionError("Audit STARTED row was not externally visible");
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
