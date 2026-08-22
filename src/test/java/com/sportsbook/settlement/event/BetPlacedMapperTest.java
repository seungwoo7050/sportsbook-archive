package com.sportsbook.settlement.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.domain.BetSlipType;
import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.RequestedSelection;
import com.sportsbook.settlement.readmodel.BetPlacement;
import com.sportsbook.settlement.readmodel.PlacementContractException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BetPlacedMapperTest {

  private final BetPlacedMapper mapper = new BetPlacedMapper();

  @Test
  void mapsCanonicalSystemFieldsAndOriginalUnitStake() {
    BetPlacedRequested event = event();

    BetPlacement placement = mapper.map(event);

    assertThat(placement.slipType()).isEqualTo(new BetSlipType.System(1, 2));
    assertThat(placement.unitStake().amount()).isEqualTo(250);
    assertThat(placement.selections()).extracting(BetPlacement.Selection::selectionId).hasSize(2);
  }

  @Test
  void rejectsLossyOddsAndNoncanonicalIdentifiers() {
    BetPlacedRequested event = event();
    event.setBetId(event.getBetId().toString().toUpperCase(java.util.Locale.ROOT));
    assertInvalid(event);

    event = event();
    event.getSelections().get(0).setOddsAtSubmission("2.00");
    assertInvalid(event);
  }

  @Test
  void rejectsSystemFieldsOnANonSystemSlip() {
    BetPlacedRequested event = event();
    event.setSlipType(BetSlipTypeTag.SINGLE);

    assertInvalid(event);
  }

  private void assertInvalid(BetPlacedRequested event) {
    assertThatThrownBy(() -> mapper.map(event)).isInstanceOf(PlacementContractException.class);
  }

  private static BetPlacedRequested event() {
    List<RequestedSelection> selections =
        List.of(selection(UUID.randomUUID()), selection(UUID.randomUUID()));
    return BetPlacedRequested.newBuilder()
        .setBetId(UUID.randomUUID().toString())
        .setUserId(UUID.randomUUID().toString())
        .setSlipType(BetSlipTypeTag.SYSTEM)
        .setSystemMinWins(1)
        .setSystemTotalSelections(2)
        .setSelections(selections)
        .setStake(Money.newBuilder().setAmount(250).setCurrency("KRW").build())
        .setIdempotencyKey("placement-key")
        .setRequestedAt(Instant.EPOCH)
        .build();
  }

  private static RequestedSelection selection(UUID selectionId) {
    return RequestedSelection.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setMarketId(UUID.randomUUID().toString())
        .setSelectionId(selectionId.toString())
        .setOddsAtSubmission("2.0000")
        .build();
  }
}
