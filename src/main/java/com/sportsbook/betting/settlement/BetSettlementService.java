package com.sportsbook.betting.settlement;

import com.sportsbook.betting.config.PermanentKafkaException;
import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.SystemBetCalculator;
import com.sportsbook.betting.domain.VoidReason;
import com.sportsbook.betting.persistence.BetRepository;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.BetSettled;
import com.sportsbook.protocol.event.BetVoided;
import com.sportsbook.protocol.value.Currency;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BetSettlementService {

  private final BetRepository bets;
  private final SystemBetCalculator calculator;

  public BetSettlementService(BetRepository bets, SystemBetCalculator calculator) {
    this.bets = bets;
    this.calculator = calculator;
  }

  @Transactional
  public void apply(BetSettled event, String payloadHash) {
    try {
      Bet bet = owned(event.getBetId(), event.getUserId());
      UUID eventId = canonical(event.getEventId());
      if (duplicateOrSuperseded(bet, eventId, payloadHash)) {
        return;
      }
      bet.settleBase(
          eventId,
          SettlementResult.valueOf(event.getResult().name()),
          money(event.getStake()),
          money(event.getPayout()),
          event.getSettledAt(),
          payloadHash);
    } catch (IllegalArgumentException | IllegalStateException failure) {
      throw permanent("Invalid settled projection", failure);
    }
  }

  @Transactional
  public void apply(BetVoided event, String payloadHash) {
    try {
      Bet bet = owned(event.getBetId(), event.getUserId());
      UUID eventId = canonical(event.getEventId());
      if (duplicateOrSuperseded(bet, eventId, payloadHash)) {
        return;
      }
      Money refund = money(event.getRefund());
      Money exposure = calculator.totalStake(bet.slipType(), bet.stake(), bet.legs().size());
      if (!refund.equals(exposure)) {
        throw new IllegalArgumentException("Void refund does not match committed exposure");
      }
      bet.voidBase(
          eventId, VoidReason.valueOf(event.getReason().name()), event.getVoidedAt(), payloadHash);
    } catch (IllegalArgumentException | IllegalStateException failure) {
      throw permanent("Invalid void projection", failure);
    }
  }

  @Transactional
  public Bet.RevisionApplyResult apply(BetResolutionRevised event, String payloadHash) {
    try {
      Bet bet = owned(event.getBetId(), event.getUserId());
      Bet.RevisionApplyResult result =
          bet.applyRevision(
              canonical(event.getEventId()),
              canonical(event.getRevisionId()),
              event.getRevisionNumber(),
              SettlementResult.valueOf(event.getPreviousResult().name()),
              SettlementResult.valueOf(event.getNewResult().name()),
              money(event.getPreviousPayout()),
              money(event.getNewPayout()),
              event.getSourceResultSettledAt(),
              event.getRevisedAt(),
              payloadHash);
      bets.flush();
      return result;
    } catch (IllegalArgumentException
        | IllegalStateException
        | DataIntegrityViolationException failure) {
      throw permanent("Invalid resolution revision", failure);
    }
  }

  private Bet owned(String rawBetId, String rawUserId) {
    UUID betId = canonical(rawBetId);
    UUID userId = canonical(rawUserId);
    Bet bet =
        bets.findLockedByBetId(betId)
            .orElseThrow(() -> new PermanentKafkaException("Resolution references unknown bet"));
    if (!bet.userId().equals(userId)) {
      throw new PermanentKafkaException("Resolution actor does not own bet");
    }
    return bet;
  }

  private static boolean duplicateOrSuperseded(Bet bet, UUID eventId, String hash) {
    if (bet.resolutionRevisionNumber() > 0) {
      return true;
    }
    if (bet.resolutionRevisionNumber() == 0) {
      if (bet.hasResolution(eventId, hash)) {
        return true;
      }
      throw new PermanentKafkaException("Conflicting base resolution replay");
    }
    return false;
  }

  private static UUID canonical(String value) {
    UUID parsed = UUID.fromString(value);
    if (!parsed.toString().equals(value)) {
      throw new IllegalArgumentException("Resolution identifier must be canonical UUID");
    }
    return parsed;
  }

  private static Money money(com.sportsbook.protocol.event.Money value) {
    return new Money(value.getAmount(), Currency.valueOf(value.getCurrency()));
  }

  private static PermanentKafkaException permanent(String message, RuntimeException failure) {
    if (failure instanceof PermanentKafkaException permanent) {
      return permanent;
    }
    return new PermanentKafkaException(message, failure);
  }
}
