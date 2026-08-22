package com.sportsbook.betting.placement;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.betting.domain.Bet;
import com.sportsbook.betting.domain.BetLeg;
import com.sportsbook.betting.domain.PlacementPhase;
import com.sportsbook.betting.domain.SystemBetCalculator;
import com.sportsbook.betting.infrastructure.id.BetReferenceGenerator;
import com.sportsbook.betting.policy.BettingPolicyProperties;
import com.sportsbook.betting.validation.BetSlipValidator;
import com.sportsbook.betting.validation.OddsSlippageChecker;
import com.sportsbook.betting.validation.OddsSnapshotReader;
import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetAssemblerTest {

  @Test
  void validatesAndFreezesOnePendingAggregate() {
    Instant now = Instant.parse("2026-08-22T00:00:00Z");
    BettingPolicyProperties policy = new BettingPolicyProperties(0, null, null, null, null);
    OddsSnapshotReader snapshots =
        new OddsSnapshotReader(null) {
          @Override
          public BigDecimal currentOdds(BetLeg ignored) {
            return new BigDecimal("2.00");
          }
        };
    BetAssembler assembler =
        new BetAssembler(
            new BetSlipValidator(policy),
            new OddsSlippageChecker(snapshots, policy),
            new SystemBetCalculator(),
            new BetReferenceGenerator(),
            Clock.fixed(now, ZoneOffset.UTC));
    PlaceBetCommand command =
        new PlaceBetCommand(
            UUID.randomUUID(),
            new BetSlipType.Single(),
            List.of(
                new PlaceBetCommand.SelectionInput(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Odds.ofDecimal("2"))),
            Money.krw(1_000),
            IdempotencyKey.of("request-1"));

    Bet bet = assembler.assemble(command, "a".repeat(64));

    assertThat(bet.placementPhase()).isEqualTo(PlacementPhase.CREATED);
    assertThat(bet.maxPayout()).isEqualTo(Money.krw(2_000));
    assertThat(bet.betReference()).startsWith("B-2026-08-22-");
  }
}
