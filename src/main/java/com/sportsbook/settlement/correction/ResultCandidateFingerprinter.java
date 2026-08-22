package com.sportsbook.settlement.correction;

import com.sportsbook.protocol.domain.SettlementResult;
import com.sportsbook.settlement.result.MatchOutcomeMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

public final class ResultCandidateFingerprinter {

  public String fingerprint(
      UUID eventId,
      MatchOutcomeMode mode,
      Map<UUID, SettlementResult> outcomes,
      Instant settledAt) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      add(digest, "result-candidate-v1");
      add(digest, eventId.toString());
      add(digest, mode.name());
      add(digest, Long.toString(settledAt.toEpochMilli()));
      if (mode != MatchOutcomeMode.VOIDED) {
        outcomes.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(
                entry -> {
                  add(digest, entry.getKey().toString());
                  add(digest, entry.getValue().name());
                });
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK must provide SHA-256", exception);
    }
  }

  private static void add(MessageDigest digest, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }
}
