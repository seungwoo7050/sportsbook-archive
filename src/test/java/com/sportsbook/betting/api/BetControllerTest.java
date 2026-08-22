package com.sportsbook.betting.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.error.ValidationFailedException;
import com.sportsbook.betting.placement.BetPlacementService;
import com.sportsbook.betting.placement.BetQueryService;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.BetStatus;
import com.sportsbook.protocol.value.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class BetControllerTest {

  @Test
  void returnsAcceptedRatherThanSuccessForDurablePendingWork() {
    BetPlacementService placement = mock(BetPlacementService.class);
    Bet bet = pendingBet();
    when(placement.place(any())).thenReturn(bet);
    BetController controller = new BetController(placement, mock(BetQueryService.class));
    UUID actorId = bet.userId();
    MockHttpServletRequest request = request(actorId);

    var response = controller.place(request, body());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody().status()).isEqualTo("PENDING");
    assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/bets/" + bet.betId());
  }

  @Test
  void returnsCreatedWithTheStablePublicBetLocation() {
    BetPlacementService placement = mock(BetPlacementService.class);
    Bet bet = pendingBet();
    when(bet.status()).thenReturn(BetStatus.ACCEPTED);
    when(placement.place(any())).thenReturn(bet);
    BetController controller = new BetController(placement, mock(BetQueryService.class));

    var response = controller.place(request(bet.userId()), body());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/bets/" + bet.betId());
  }

  @Test
  void rejectsAmbiguousActorHeaders() {
    MockHttpServletRequest request = request(UUID.randomUUID());
    request.addHeader("X-User-Id", UUID.randomUUID().toString());

    assertThatThrownBy(
            () ->
                new BetController(mock(BetPlacementService.class), mock(BetQueryService.class))
                    .byId(request, UUID.randomUUID()))
        .isInstanceOf(ValidationFailedException.class);
  }

  private static MockHttpServletRequest request(UUID actorId) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/bets");
    request.addHeader("X-User-Id", actorId.toString());
    request.addHeader("Idempotency-Key", "request-1");
    return request;
  }

  private static PlaceBetRequest body() {
    return new PlaceBetRequest(
        new PlaceBetRequest.SlipTypeRequest("SINGLE", null, null),
        List.of(
            new PlaceBetRequest.SelectionRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("2.00"))),
        Money.krw(1_000));
  }

  private static Bet pendingBet() {
    Bet bet = mock(Bet.class);
    when(bet.betId()).thenReturn(UUID.randomUUID());
    when(bet.betReference()).thenReturn("B-2026-08-22-00000000");
    when(bet.userId()).thenReturn(UUID.randomUUID());
    when(bet.slipType()).thenReturn(new BetSlipType.Single());
    when(bet.status()).thenReturn(BetStatus.PENDING);
    when(bet.stake()).thenReturn(Money.krw(1_000));
    when(bet.maxPayout()).thenReturn(Money.krw(2_000));
    when(bet.legs()).thenReturn(List.of());
    when(bet.createdAt()).thenReturn(Instant.EPOCH);
    return bet;
  }
}
