package com.sportsbook.betting.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sportsbook.betting.config.PermanentKafkaException;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.betting.settlement.BetSettlementService;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.SettlementResultAvro;
import com.sportsbook.protocol.value.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetRevisionChronologyTest {

  private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

  @Test
  void rejectsImpossibleChronologyBeforeLowerOrDuplicateOutcomes() {
    Bet bet = BetTest.accepted(new BetSlipType.Single(), List.of(BetTest.leg("2.00")));
    UUID eventId = bet.legs().get(0).eventId();
    UUID revisionId = UUID.randomUUID();
    String revisionHash = "a".repeat(64);
    bet.applyRevision(
        eventId,
        revisionId,
        2,
        SettlementResult.LOST,
        SettlementResult.WON,
        Money.krw(0),
        Money.krw(2_000),
        NOW,
        NOW.plusSeconds(1),
        revisionHash);
    BetRepository bets = mock(BetRepository.class);
    when(bets.findLockedByBetId(bet.betId())).thenReturn(java.util.Optional.of(bet));
    BetSettlementService service = new BetSettlementService(bets, new SystemBetCalculator());

    assertPermanentChronologyFailure(
        service, revised(bet, eventId, UUID.randomUUID(), 1), "b".repeat(64));
    assertPermanentChronologyFailure(service, revised(bet, eventId, revisionId, 2), revisionHash);
  }

  private static void assertPermanentChronologyFailure(
      BetSettlementService service, BetResolutionRevised event, String payloadHash) {
    assertThatThrownBy(() -> service.apply(event, payloadHash))
        .isInstanceOf(PermanentKafkaException.class)
        .hasMessage("Invalid resolution revision")
        .hasRootCauseMessage("sourceSettledAt must not be after revisedAt");
  }

  private static BetResolutionRevised revised(
      Bet bet, UUID eventId, UUID revisionId, long revisionNumber) {
    return BetResolutionRevised.newBuilder()
        .setBetId(bet.betId().toString())
        .setUserId(bet.userId().toString())
        .setEventId(eventId.toString())
        .setRevisionId(revisionId.toString())
        .setRevisionNumber(revisionNumber)
        .setPreviousResult(SettlementResultAvro.LOST)
        .setNewResult(SettlementResultAvro.WON)
        .setPreviousPayout(eventMoney(0))
        .setNewPayout(eventMoney(2_000))
        .setSourceResultSettledAt(NOW.plusSeconds(2))
        .setRevisedAt(NOW.plusSeconds(1))
        .build();
  }

  private static com.sportsbook.protocol.event.Money eventMoney(long amount) {
    return com.sportsbook.protocol.event.Money.newBuilder()
        .setAmount(amount)
        .setCurrency("KRW")
        .build();
  }
}
