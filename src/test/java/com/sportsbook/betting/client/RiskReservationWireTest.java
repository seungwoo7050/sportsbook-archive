package com.sportsbook.betting.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sportsbook.betting.error.ValidationFailedException;
import com.sportsbook.protocol.value.Money;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

class RiskReservationWireTest {

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void sendsFullExposureAndReadsReservationToken() throws Exception {
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID selectionId = UUID.randomUUID();
    String json =
        mapper.writeValueAsString(
            RiskReservationRequest.of(betId, userId, Money.krw(6_000), List.of(selectionId)));

    assertThat(json).contains("\"betId\":\"" + betId + "\"");
    assertThat(json).contains("\"amount\":6000");
    assertThat(json).contains("\"selectionIds\":[\"" + selectionId + "\"]");

    RiskReservationResponse response =
        mapper.readValue(
            "{\"approved\":true,\"replayed\":false,\"reservationState\":\"RESERVED\","
                + "\"expiresAt\":\"2026-08-22T00:02:00Z\",\"reservationToken\":\""
                + "a".repeat(64)
                + "\"}",
            RiskReservationResponse.class);
    assertThat(response.reservationToken()).isEqualTo("a".repeat(64));
    assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-08-22T00:02:00Z"));
  }

  @Test
  void recognizesOnlyTheFixedValidationProblemWithoutItsDetail() {
    String body =
        "{\"status\":400,\"errorCode\":\"VALIDATION_FAILED\","
            + "\"detail\":\"provider internal validation detail\"}";
    RuntimeException verdict =
        RiskProblem.mapReservation(
            new MockClientHttpResponse(
                body.getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST));

    assertThat(verdict)
        .isInstanceOf(ValidationFailedException.class)
        .hasMessage("Risk rejected an invalid reservation");
  }
}
