package com.sportsbook.risk.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.risk.policy.PatternAction;
import org.junit.jupiter.api.Test;

class ReservationWireMapperTest {
  private static final String TOKEN = "a".repeat(64);
  private final ReservationWireMapper mapper = new ReservationWireMapper(new ObjectMapper());

  @Test
  void decodesPrecisionSafeApprovalAndPatterns() {
    String patterns = "[{\"rule\":\"rapid-betting\",\"action\":\"SUSPECT\",\"reason\":\"rapid\"}]";

    ReservationWireMapper.Decoded decoded = mapper.map(approved("2", patterns, ""));

    assertThat(decoded.expired()).isEqualTo(2);
    assertThat(decoded.decision().state()).isEqualTo(ReservationState.RESERVED);
    assertThat(decoded.decision().token()).isEqualTo(TOKEN);
    assertThat(decoded.decision().patterns())
        .singleElement()
        .satisfies(
            pattern -> {
              assertThat(pattern.rule()).isEqualTo("rapid-betting");
              assertThat(pattern.action()).isEqualTo(PatternAction.SUSPECT);
            });
  }

  @Test
  void decodesRejectionsAndConflicts() {
    ReservationDecision rejected =
        mapper
            .map(
                "{\"version\":\"1\",\"expired\":\"0\",\"status\":\"REJECTED\","
                    + "\"replayed\":true,\"rejection\":\"LIMIT\",\"patternsJson\":\"[]\"}")
            .decision();
    ReservationDecision conflict =
        mapper
            .map(
                "{\"version\":\"1\",\"expired\":\"0\",\"status\":\"CONFLICT\","
                    + "\"replayed\":false}")
            .decision();

    assertThat(rejected.rejection()).isEqualTo("LIMIT");
    assertThat(rejected.replayed()).isTrue();
    assertThat(conflict.status()).isEqualTo(ReservationDecision.Status.CONFLICT);
  }

  @Test
  void rejectsUnknownFieldsAndMalformedPatternEvidence() {
    assertThatThrownBy(() -> mapper.map(approved("0", "[]", ",\"extra\":1")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> mapper.map(approved("0", "{}", "")))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> mapper.map(null)).isInstanceOf(IllegalStateException.class);
  }

  private static String approved(String expired, String patterns, String extra) {
    String escaped = patterns.replace("\\", "\\\\").replace("\"", "\\\"");
    return "{\"version\":\"1\",\"expired\":\""
        + expired
        + "\",\"status\":\"APPROVED\",\"state\":\"RESERVED\",\"expiresAt\":\"10\","
        + "\"token\":\""
        + TOKEN
        + "\",\"replayed\":false,\"patternsJson\":\""
        + escaped
        + "\""
        + extra
        + "}";
  }
}
