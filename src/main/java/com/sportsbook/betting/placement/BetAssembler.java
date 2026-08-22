package com.sportsbook.betting.placement;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetDraft;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.domain.SystemBetCalculator;
import com.sportsbook.betting.infrastructure.id.BetReferenceGenerator;
import com.sportsbook.betting.infrastructure.id.UuidV7;
import com.sportsbook.betting.validation.BetSlipValidator;
import com.sportsbook.betting.validation.OddsSlippageChecker;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BetAssembler {

  private final BetSlipValidator validator;
  private final OddsSlippageChecker slippage;
  private final SystemBetCalculator calculator;
  private final BetReferenceGenerator references;
  private final Clock clock;

  public BetAssembler(
      BetSlipValidator validator,
      OddsSlippageChecker slippage,
      SystemBetCalculator calculator,
      BetReferenceGenerator references,
      Clock clock) {
    this.validator = validator;
    this.slippage = slippage;
    this.calculator = calculator;
    this.references = references;
    this.clock = clock;
  }

  public Bet assemble(PlaceBetCommand command, String fingerprint) {
    List<BetLeg> legs =
        command.selections().stream()
            .map(
                input ->
                    BetLeg.create(
                        input.eventId(),
                        input.marketId(),
                        input.selectionId(),
                        input.oddsAtSubmission()))
            .toList();
    validator.validate(command.slipType(), legs);
    validator.validateStake(command.unitStake());
    slippage.check(legs);
    List<Odds> odds = legs.stream().map(BetLeg::oddsAtSubmission).toList();
    Money maxPayout = calculator.maxPayout(command.slipType(), command.unitStake(), odds);
    Instant now = clock.instant();
    BetDraft draft =
        new BetDraft(
            UuidV7.generate(),
            command.userId(),
            references.next(now),
            command.slipType(),
            command.unitStake(),
            maxPayout,
            command.idempotencyKey(),
            fingerprint,
            now);
    return Bet.pending(draft, legs);
  }
}
