package com.sportsbook.risk.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsbook.protocol.event.BetPlacedRequested;
import com.sportsbook.protocol.event.BetSlipTypeTag;
import com.sportsbook.protocol.event.Money;
import com.sportsbook.protocol.event.RequestedSelection;
import com.sportsbook.protocol.value.Currency;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcceptedBetEnvelopeTest {
  private static final Instant REQUESTED_AT = Instant.parse("2026-08-21T06:00:00Z");
  private static final Instant OBSERVED_AT = REQUESTED_AT.plusSeconds(1);
  private static final String USER = "10000000-0000-4000-8000-000000000001";

  @Test
  void decodesTypedSystemExposureAndRecomputesTheReservationFingerprint() {
    AcceptedBetEnvelope envelope = decode(validEvent(), USER);

    assertThat(envelope.command().userId().value().toString()).isEqualTo(USER);
    assertThat(envelope.command().stake().amount()).isEqualTo(300L);
    assertThat(envelope.command().stake().currency()).isEqualTo(Currency.USD);
    assertThat(envelope.command().selectionIds()).hasSize(3).doesNotHaveDuplicates();
    assertThat(envelope.command().now()).isEqualTo(OBSERVED_AT);
    assertThat(envelope.requestedAt()).isEqualTo(REQUESTED_AT);
    assertThat(envelope.reservationFingerprint())
        .isEqualTo("606ff15f92f1e6fc874679165bbbc550258e21e1c51354b249d9bd57376b4e22");
  }

  @Test
  void rejectsMalformedTypedIdsAndMismatchedKafkaKeys() {
    BetPlacedRequested malformed = validEvent();
    malformed.setBetId("not-a-uuid");
    assertThatThrownBy(() -> decode(malformed, USER)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> decode(validEvent(), "10000000-0000-4000-8000-000000000002"))
        .hasMessageContaining("Kafka key");
  }

  @Test
  void rejectsDuplicateTypedSelections() {
    BetPlacedRequested event = validEvent();
    event.setSelections(List.of(selection(1), selection(1), selection(2)));
    assertThatThrownBy(() -> decode(event, USER)).hasMessageContaining("unique");
  }

  @Test
  void rejectsUnsupportedCurrenciesAndInvalidSlipShapes() {
    BetPlacedRequested currency = validEvent();
    currency.setStake(new Money(100L, "EUR"));
    assertThatThrownBy(() -> decode(currency, USER)).isInstanceOf(IllegalArgumentException.class);

    BetPlacedRequested shape = validEvent();
    shape.setSystemTotalSelections(4);
    assertThatThrownBy(() -> decode(shape, USER)).hasMessageContaining("totalSelections");
  }

  @Test
  void validatesDiscardedSelectionAndIdempotencyFields() {
    BetPlacedRequested event = validEvent();
    event.getSelections().get(0).setEventId("bad-event-id");
    assertThatThrownBy(() -> decode(event, USER)).isInstanceOf(IllegalArgumentException.class);

    BetPlacedRequested key = validEvent();
    key.setIdempotencyKey("\n");
    assertThatThrownBy(() -> decode(key, USER)).isInstanceOf(IllegalArgumentException.class);
  }

  private static AcceptedBetEnvelope decode(BetPlacedRequested event, String kafkaKey) {
    return AcceptedBetEnvelope.decode(kafkaKey, AvroCodec.encode(event), OBSERVED_AT);
  }

  private static BetPlacedRequested validEvent() {
    return BetPlacedRequested.newBuilder()
        .setBetId("20000000-0000-4000-8000-000000000001")
        .setUserId(USER)
        .setSlipType(BetSlipTypeTag.SYSTEM)
        .setSystemMinWins(2)
        .setSystemTotalSelections(3)
        .setSelections(List.of(selection(1), selection(2), selection(3)))
        .setStake(new Money(100L, "USD"))
        .setIdempotencyKey("accepted-envelope-test")
        .setRequestedAt(REQUESTED_AT)
        .build();
  }

  private static RequestedSelection selection(int suffix) {
    String tail = String.format("%012d", suffix);
    return new RequestedSelection(
        "30000000-0000-4000-8000-" + tail,
        "40000000-0000-4000-8000-" + tail,
        "50000000-0000-4000-8000-" + tail,
        "1.8500");
  }
}
