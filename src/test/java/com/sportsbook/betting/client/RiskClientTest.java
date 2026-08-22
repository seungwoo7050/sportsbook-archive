package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sportsbook.betting.error.DependencyUnavailableException;
import com.sportsbook.betting.error.RiskLimitException;
import com.sportsbook.betting.error.ValidationFailedException;
import com.sportsbook.protocol.value.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RiskClientTest {

  private MockRestServiceServer server;
  private RiskClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://risk");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new RiskClient(builder.build());
  }

  @Test
  void returnsOpaqueTokenForApprovedFullExposure() {
    String token = "a".repeat(64);
    server
        .expect(requestTo("http://risk/internal/v1/risk/reservations"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.stake.amount").value(6_000))
        .andRespond(
            withSuccess(
                "{\"approved\":true,\"replayed\":false,\"reservationState\":\"RESERVED\","
                    + "\"expiresAt\":\"2026-08-22T00:02:00Z\",\"reservationToken\":\""
                    + token
                    + "\"}",
                MediaType.APPLICATION_JSON));

    RiskClient.Reservation reservation = reserve();

    assertThat(reservation.token()).isEqualTo(token);
    server.verify();
  }

  @Test
  void treatsApprovedFalseAsBusinessVerdict() {
    server
        .expect(requestTo("http://risk/internal/v1/risk/reservations"))
        .andRespond(
            withSuccess(
                "{\"approved\":false,\"replayed\":false,"
                    + "\"rejectionReason\":\"daily limit\",\"patterns\":[]}",
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(this::reserve)
        .isInstanceOf(RiskLimitException.class)
        .hasMessage("daily limit");
  }

  @Test
  void acceptsOnlyHttp200OrTheFixedValidationVerdict() {
    server
        .expect(requestTo("http://risk/internal/v1/risk/reservations"))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"errorCode\":\"VALIDATION_FAILED\",\"detail\":\"hidden\"}"));
    assertThatThrownBy(this::reserve)
        .isInstanceOf(ValidationFailedException.class)
        .hasMessage("Risk rejected an invalid reservation");
    server.reset();
    server
        .expect(requestTo("http://risk/internal/v1/risk/reservations"))
        .andRespond(withStatus(HttpStatus.CREATED));
    assertThatThrownBy(this::reserve).isInstanceOf(DependencyUnavailableException.class);
  }

  @Test
  void presentsPersistedTokenWhenCommitting() {
    UUID betId = UUID.randomUUID();
    String token = "b".repeat(64);
    server
        .expect(requestTo("http://risk/internal/v1/risk/reservations/" + betId + "/commit"))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header("X-Risk-Reservation-Token", token))
        .andRespond(withNoContent());

    assertThat(client.commit(betId, token)).isEqualTo(RiskClient.CommitResult.COMMITTED);
    server.verify();
  }

  @Test
  void returnsFalseForExpiredReservation() {
    UUID betId = UUID.randomUUID();
    server
        .expect(requestTo("http://risk/internal/v1/risk/reservations/" + betId + "/commit"))
        .andRespond(withResourceNotFound());

    assertThat(client.commit(betId, "c".repeat(64))).isEqualTo(RiskClient.CommitResult.NOT_FOUND);
  }

  @Test
  void returnsDefinitiveConflictWithoutRetryingCommit() {
    UUID betId = UUID.randomUUID();
    server
        .expect(requestTo("http://risk/internal/v1/risk/reservations/" + betId + "/commit"))
        .andRespond(
            org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(
                HttpStatus.CONFLICT));

    assertThat(client.commit(betId, "d".repeat(64))).isEqualTo(RiskClient.CommitResult.CONFLICT);
  }

  @Test
  void acceptsOnlyNoContentForCommit() {
    UUID betId = UUID.randomUUID();
    for (HttpStatus status : List.of(HttpStatus.OK, HttpStatus.FOUND)) {
      server
          .expect(requestTo("http://risk/internal/v1/risk/reservations/" + betId + "/commit"))
          .andRespond(withStatus(status));
      assertThatThrownBy(() -> client.commit(betId, "e".repeat(64)))
          .isInstanceOf(DependencyUnavailableException.class);
      server.reset();
    }
  }
  private RiskClient.Reservation reserve() {
    return client.reserve(
        UUID.randomUUID(), UUID.randomUUID(), Money.krw(6_000), List.of(UUID.randomUUID()));
  }
}
