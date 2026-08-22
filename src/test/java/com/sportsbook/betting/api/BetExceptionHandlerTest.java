package com.sportsbook.betting.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.betting.error.BetNotFoundException;
import com.sportsbook.betting.error.InsufficientBalanceException;
import com.sportsbook.protocol.error.ErrorCode;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class BetExceptionHandlerTest {

  private final BetExceptionHandler handler = new BetExceptionHandler();

  @Test
  void preservesSharedPlacementStatusAndErrorCode() {
    var response =
        handler.placement(
            new InsufficientBalanceException("declined"), request("/internal/v1/bets"));

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE.name());
    assertThat(response.getBody().instance()).isEqualTo(URI.create("/internal/v1/bets"));
  }

  @Test
  void rendersActorScopedMissingBetsAsNotFound() {
    var response =
        handler.missing(new BetNotFoundException("hidden"), request("/internal/v1/bets/unknown"));

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getBody().errorCode()).isEqualTo("BET_NOT_FOUND");
  }

  @Test
  void normalizesMalformedWireValuesAsValidationProblems() {
    var response =
        handler.invalid(new IllegalArgumentException("bad uuid"), request("/internal/v1/bets"));

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody().errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
    assertThat(response.getBody().detail()).isEqualTo("Request validation failed");
    assertThat(response.getBody().detail()).doesNotContain("bad uuid");
  }

  private static MockHttpServletRequest request(String path) {
    return new MockHttpServletRequest("POST", path);
  }
}
