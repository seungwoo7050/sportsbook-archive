package com.sportsbook.protocol.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BetSlipTypeTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void singleAndMultipleEmptyRecordsCompareEqualByValue() {
    assertThat(new BetSlipType.Single()).isEqualTo(new BetSlipType.Single());
    assertThat(new BetSlipType.Multiple()).isEqualTo(new BetSlipType.Multiple());
  }

  @Test
  void systemAcceptsValidBounds() {
    BetSlipType.System sys = new BetSlipType.System(2, 3);
    assertThat(sys.minWins()).isEqualTo(2);
    assertThat(sys.totalSelections()).isEqualTo(3);
  }

  @Test
  void systemBoundaryAcceptsMinAndMaxTotal() {
    new BetSlipType.System(1, 2);
    new BetSlipType.System(15, 15);
  }

  @Test
  void systemRejectsTotalSelectionsBelowMin() {
    assertThatIllegalArgumentException().isThrownBy(() -> new BetSlipType.System(1, 1));
  }

  @Test
  void systemRejectsTotalSelectionsAboveMax() {
    assertThatIllegalArgumentException().isThrownBy(() -> new BetSlipType.System(1, 16));
  }

  @Test
  void systemRejectsMinWinsBelowOne() {
    assertThatIllegalArgumentException().isThrownBy(() -> new BetSlipType.System(0, 3));
  }

  @Test
  void systemRejectsMinWinsAboveTotalSelections() {
    assertThatIllegalArgumentException().isThrownBy(() -> new BetSlipType.System(4, 3));
  }

  @Test
  void singleJsonRoundTrip() throws Exception {
    BetSlipType original = new BetSlipType.Single();
    String json = mapper.writeValueAsString(original);
    assertThat(json).isEqualTo("{\"type\":\"SINGLE\"}");
    assertThat(mapper.readValue(json, BetSlipType.class)).isEqualTo(original);
  }

  @Test
  void multipleJsonRoundTrip() throws Exception {
    BetSlipType original = new BetSlipType.Multiple();
    String json = mapper.writeValueAsString(original);
    assertThat(json).isEqualTo("{\"type\":\"MULTIPLE\"}");
    assertThat(mapper.readValue(json, BetSlipType.class)).isEqualTo(original);
  }

  @Test
  void systemJsonRoundTripCarriesMinWinsAndTotal() throws Exception {
    BetSlipType original = new BetSlipType.System(2, 3);
    String json = mapper.writeValueAsString(original);
    assertThat(json).isEqualTo("{\"type\":\"SYSTEM\",\"minWins\":2,\"totalSelections\":3}");
    assertThat(mapper.readValue(json, BetSlipType.class)).isEqualTo(original);
  }

  @Test
  void instanceofPatternsDispatchOverEachVariant() {
    assertThat(label(new BetSlipType.Single())).isEqualTo("single");
    assertThat(label(new BetSlipType.Multiple())).isEqualTo("multiple");
    assertThat(label(new BetSlipType.System(2, 3))).isEqualTo("system-2-of-3");
  }

  private String label(BetSlipType type) {
    if (type instanceof BetSlipType.System sys) {
      return "system-" + sys.minWins() + "-of-" + sys.totalSelections();
    }
    if (type instanceof BetSlipType.Multiple) {
      return "multiple";
    }
    if (type instanceof BetSlipType.Single) {
      return "single";
    }
    throw new IllegalStateException("Unknown: " + type);
  }
}
