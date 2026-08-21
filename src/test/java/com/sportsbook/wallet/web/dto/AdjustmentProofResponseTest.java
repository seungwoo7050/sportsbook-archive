package com.sportsbook.wallet.web.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sportsbook.protocol.value.IdempotencyKey;
import com.sportsbook.protocol.value.Money;
import com.sportsbook.wallet.domain.WalletAdjustment;
import com.sportsbook.wallet.service.command.AdjustmentCommand;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdjustmentProofResponseTest {
  private static final UUID REVISION_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004a1");
  private static final UUID BET_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004a2");
  private static final UUID USER_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004a3");
  private static final UUID GROUP_ID = UUID.fromString("019b76da-a000-7000-8000-0000000004a4");
  private static final Instant NOW = Instant.parse("2026-08-21T19:00:00Z");
  private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

  @Test
  void serializesRejectedProofsWithEveryNullableKeyPresent() {
    JsonNode json = json(WalletAdjustment.rejected(command(1_000L, 700L), NOW));
    List<String> fields = new ArrayList<>();
    json.fieldNames().forEachRemaining(fields::add);

    assertThat(fields)
        .containsExactly(
            "revisionId",
            "betId",
            "revisionNumber",
            "userId",
            "previousPayout",
            "newPayout",
            "deltaAmount",
            "currency",
            "status",
            "queueSequence",
            "operationGroupId",
            "queuedAt",
            "appliedAt",
            "nextAttemptAt");
    assertThat(json.get("revisionId").textValue()).isEqualTo(REVISION_ID.toString());
    assertThat(json.get("betId").textValue()).isEqualTo(BET_ID.toString());
    assertThat(json.get("revisionNumber").longValue()).isEqualTo(2L);
    assertThat(json.get("userId").textValue()).isEqualTo(USER_ID.toString());
    assertThat(json.at("/previousPayout/amount").longValue()).isEqualTo(1_000L);
    assertThat(json.at("/newPayout/amount").longValue()).isEqualTo(700L);
    assertThat(json.get("currency").textValue()).isEqualTo("KRW");
    assertThat(json.get("status").textValue()).isEqualTo("REJECTED");
    assertThat(json.get("deltaAmount").longValue()).isEqualTo(-300L);
    assertThat(
            List.of("queueSequence", "operationGroupId", "queuedAt", "appliedAt", "nextAttemptAt"))
        .allSatisfy(field -> assertThat(json.has(field) && json.get(field).isNull()).isTrue());
    assertThatNullPointerException().isThrownBy(() -> AdjustmentProofResponse.from(null));
  }

  @Test
  void mapsBlockedAndImmediateAppliedProofMetadata() {
    JsonNode blocked = json(WalletAdjustment.blocked(command(1_000L, 700L), 4L, NOW));
    JsonNode applied = json(WalletAdjustment.applied(command(700L, 1_000L), GROUP_ID, NOW));

    assertThat(blocked.get("status").textValue()).isEqualTo("BLOCKED");
    assertThat(blocked.get("queueSequence").longValue()).isEqualTo(4L);
    assertThat(blocked.get("queuedAt").isNull()).isFalse();
    assertThat(blocked.get("nextAttemptAt").isNull()).isFalse();
    assertThat(blocked.get("operationGroupId").isNull()).isTrue();
    assertThat(blocked.get("appliedAt").isNull()).isTrue();
    assertThat(applied.get("status").textValue()).isEqualTo("APPLIED");
    assertThat(applied.get("deltaAmount").longValue()).isEqualTo(300L);
    assertThat(applied.get("operationGroupId").textValue()).isEqualTo(GROUP_ID.toString());
    assertThat(applied.get("queueSequence").isNull()).isTrue();
    assertThat(applied.get("queuedAt").isNull()).isTrue();
    assertThat(applied.get("nextAttemptAt").isNull()).isTrue();
    assertThat(applied.get("appliedAt").longValue()).isEqualTo(NOW.getEpochSecond());
  }

  private JsonNode json(WalletAdjustment proof) {
    return JSON.valueToTree(AdjustmentProofResponse.from(proof));
  }

  private AdjustmentCommand command(long previous, long next) {
    return new AdjustmentCommand(
        REVISION_ID,
        BET_ID,
        2L,
        USER_ID,
        Money.krw(previous),
        Money.krw(next),
        IdempotencyKey.of("settlement:revision:" + REVISION_ID));
  }
}
