package com.sportsbook.betting.settlement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.config.PermanentKafkaException;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.domain.SystemBetCalculator;
import com.sportsbook.betting.domain.VoidReason;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import com.sportsbook.protocol.event.SettlementResultAvro;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetSettlementServiceTest {

  @Test
  void projectsSettledSystemEventUsingItsOriginalUnitStake() {
    BetRepository bets = mock(BetRepository.class);
    Bet bet = mock(Bet.class);
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    when(bet.userId()).thenReturn(userId);
    when(bet.resolutionRevisionNumber()).thenReturn(-1L);
    when(bets.findLockedByBetId(betId)).thenReturn(Optional.of(bet));
    BetSettled event =
        BetSettled.newBuilder()
            .setBetId(betId.toString())
            .setUserId(userId.toString())
            .setEventId(eventId.toString())
            .setResult(SettlementResultAvro.WON)
            .setStake(eventMoney(1_000))
            .setPayout(eventMoney(2_600))
            .setSettledAt(Instant.EPOCH)
            .setResultDetail(java.util.Map.of())
            .build();

    new BetSettlementService(bets, new SystemBetCalculator()).apply(event, "a".repeat(64));

    verify(bet)
        .settleBase(
            eventId,
            SettlementResult.WON,
            Money.krw(1_000),
            Money.krw(2_600),
            Instant.EPOCH,
            "a".repeat(64));
  }

  @Test
  void validatesWholeSlipVoidAgainstCommittedSystemExposure() {
    BetRepository bets = mock(BetRepository.class);
    Bet bet = mock(Bet.class);
    UUID betId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    when(bet.userId()).thenReturn(userId);
    when(bet.resolutionRevisionNumber()).thenReturn(-1L);
    when(bet.slipType()).thenReturn(new BetSlipType.System(2, 3));
    when(bet.stake()).thenReturn(Money.krw(1_000));
    when(bet.legs())
        .thenReturn(List.of(mock(BetLeg.class), mock(BetLeg.class), mock(BetLeg.class)));
    when(bets.findLockedByBetId(betId)).thenReturn(Optional.of(bet));
    BetVoided event =
        BetVoided.newBuilder()
            .setBetId(betId.toString())
            .setUserId(userId.toString())
            .setEventId(eventId.toString())
            .setReason(com.sportsbook.protocol.event.VoidReason.EVENT_CANCELLED)
            .setRefund(eventMoney(3_000))
            .setVoidedAt(Instant.EPOCH)
            .build();

    new BetSettlementService(bets, new SystemBetCalculator()).apply(event, "b".repeat(64));

    verify(bet).voidBase(eventId, VoidReason.EVENT_CANCELLED, Instant.EPOCH, "b".repeat(64));
  }

  @Test
  void classifiesAResolutionActorMismatchAsPermanent() {
    BetRepository bets = mock(BetRepository.class);
    Bet bet = mock(Bet.class);
    UUID betId = UUID.randomUUID();
    when(bet.userId()).thenReturn(UUID.randomUUID());
    when(bets.findLockedByBetId(betId)).thenReturn(Optional.of(bet));
    BetSettled event =
        BetSettled.newBuilder()
            .setBetId(betId.toString())
            .setUserId(UUID.randomUUID().toString())
            .setEventId(UUID.randomUUID().toString())
            .setResult(SettlementResultAvro.WON)
            .setStake(eventMoney(1_000))
            .setPayout(eventMoney(2_000))
            .setSettledAt(Instant.EPOCH)
            .setResultDetail(java.util.Map.of())
            .build();

    assertThatThrownBy(
            () ->
                new BetSettlementService(bets, new SystemBetCalculator())
                    .apply(event, "c".repeat(64)))
        .isInstanceOf(PermanentKafkaException.class)
        .hasMessageContaining("actor");
  }

  private static com.sportsbook.protocol.event.Money eventMoney(long amount) {
    return com.sportsbook.protocol.event.Money.newBuilder()
        .setAmount(amount)
        .setCurrency("KRW")
        .build();
  }
}
