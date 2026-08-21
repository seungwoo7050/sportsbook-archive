package com.sportsbook.risk.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.value.SelectionId;
import com.sportsbook.risk.counter.LimitType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskSnapshotWireMapperTest {
  private static final SelectionId SELECTION = SelectionId.of(new UUID(0, 7));
  private final RiskSnapshotWireMapper mapper = new RiskSnapshotWireMapper(new ObjectMapper());

  @Test
  void mapsPrecisionSafeCombinedFacts() {
    RiskSnapshotWireMapper.Decoded decoded =
        mapper.map(payload("2", "25", null), List.of(SELECTION));

    assertThat(decoded.expired()).isEqualTo(2);
    assertThat(decoded.snapshot().limits().require(LimitType.STAKE_DAILY))
        .isEqualTo(new LimitSnapshot.Value(100, 25, null));
    assertThat(decoded.snapshot().patterns().recentBetCount().valueOrThrow()).isEqualTo(4);
    assertThat(decoded.snapshot().patterns().recentStakes().valueOrThrow())
        .containsExactly(10L, 20L);
    assertThat(decoded.snapshot().patterns().selectionCount(SELECTION).valueOrThrow()).isEqualTo(3);
  }

  @Test
  void preservesDeferredSlotFailures() {
    String failure = "{\"ok\":false,\"error\":\"counter unavailable\"}";
    RiskSnapshot snapshot = mapper.map(payload("0", null, failure), List.of(SELECTION)).snapshot();

    assertThatThrownBy(() -> snapshot.limits().require(LimitType.STAKE_DAILY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("counter unavailable");
  }

  @Test
  void rejectsUnknownFieldsAndNoncanonicalIntegers() {
    assertThatThrownBy(() -> mapper.map(payload("01", "0", null), List.of(SELECTION)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                mapper.map(
                    payload("0", "0", null)
                        .replace("\"version\":\"1\"", "\"version\":\"1\",\"extra\":1"),
                    List.of(SELECTION)))
        .isInstanceOf(IllegalStateException.class);
  }

  private static String payload(String expired, String active, String dailyReplacement) {
    String success =
        "{\"ok\":true,\"committed\":\"100\",\"active\":\"" + active + "\",\"override\":null}";
    String daily = dailyReplacement == null ? success : dailyReplacement;
    String zero = "{\"ok\":true,\"committed\":\"0\",\"active\":\"0\",\"override\":null}";
    return "{\"version\":\"1\",\"expired\":\""
        + expired
        + "\",\"limits\":{"
        + "\"STAKE_DAILY\":"
        + daily
        + ",\"STAKE_WEEKLY\":"
        + zero
        + ",\"STAKE_MONTHLY\":"
        + zero
        + ",\"SELECTIONS_PER_MINUTE\":"
        + zero
        + "},\"patterns\":{\"rapid\":{\"ok\":true,\"value\":\"4\"},"
        + "\"stakes\":{\"ok\":true,\"value\":\"10,20\"},"
        + "\"selections\":[{\"selectionId\":\""
        + SELECTION.value()
        + "\",\"slot\":{\"ok\":true,\"value\":\"3\"}}]}}";
  }
}
