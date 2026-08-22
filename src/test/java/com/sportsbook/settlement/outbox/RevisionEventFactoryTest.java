package com.sportsbook.settlement.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.protocol.event.BetResolutionRevised;
import com.sportsbook.protocol.event.SettlementResultAvro;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.protocol.value.Odds;
import com.sportsbook.settlement.config.SettlementTopics;
import com.sportsbook.settlement.correction.RevisionPlan;
import com.sportsbook.settlement.correction.RevisionTarget;
import com.sportsbook.settlement.event.StrictAvroDecoder;
import com.sportsbook.settlement.resolver.ResolvedSelection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevisionEventFactoryTest {

  @Test
  void preservesRevisionIdentityAndUsesBetIdAsTheKafkaKey() {
    RevisionPlan plan = plan();
    Instant revisedAt = Instant.EPOCH.plusSeconds(2);
    SettlementEventFactory factory =
        new SettlementEventFactory(
            new SettlementTopics(null, null, null, null, null, null), new StrictAvroEncoder());

    OutboxEvent outbox = factory.revised(plan, revisedAt);
    BetResolutionRevised event =
        new StrictAvroDecoder().decode(outbox.payload(), BetResolutionRevised.class);

    assertThat(outbox.topic()).isEqualTo("bet.resolution.revised.v1");
    assertThat(outbox.partitionKey()).isEqualTo(plan.target().betId().toString());
    assertThat(event.getRevisionId()).isEqualTo(plan.revisionId().toString());
    assertThat(event.getRevisionNumber()).isEqualTo(1);
    assertThat(event.getPreviousResult()).isEqualTo(SettlementResultAvro.WON);
    assertThat(event.getNewResult()).isEqualTo(SettlementResultAvro.PUSH);
    assertThat(event.getPreviousPayout().getAmount()).isEqualTo(200);
    assertThat(event.getNewPayout().getAmount()).isEqualTo(100);
    assertThat(event.getSourceResultSettledAt()).isEqualTo(Instant.EPOCH.plusSeconds(1));
    assertThat(event.getRevisedAt()).isEqualTo(revisedAt);
  }

  private static RevisionPlan plan() {
    RevisionTarget target =
        new RevisionTarget(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementResult.WON,
            Money.krw(200),
            new BetSlipType.Single(),
            Money.krw(100),
            List.of(
                new ResolvedSelection(
                    UUID.randomUUID(), Odds.ofDecimal("2.0000"), SettlementResult.PUSH)),
            Instant.EPOCH.plusSeconds(1));
    return new RevisionPlan(
        UUID.randomUUID(), target, SettlementResult.PUSH, Money.krw(100), Instant.EPOCH);
  }
}
