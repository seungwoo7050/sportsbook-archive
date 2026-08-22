package com.sportsbook.betting.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class MigrationContractTest {

  @Test
  void preservesInitialBetSchema() {
    assertThat(sha256("V1__bet_and_leg.sql"))
        .isEqualTo("ecab4d9c22ab9bc83b35999db9c8a5c08abf8a80fc8e9263f23bcd369a84f29c");
  }

  @Test
  void preservesTransactionalOutboxSchema() {
    assertThat(sha256("V2__outbox.sql"))
        .isEqualTo("28161d23320d94a41d17b64a1dd0e2c9513fdfa74ac10ea1fb86bc4edf2c3d39");
  }

  @Test
  void preservesSettlementOutcomeSchema() {
    assertThat(sha256("V3__settlement_outcome.sql"))
        .isEqualTo("a57b6a695e8a94624d1e62fe4719e5ce384bc6cec00236d11b68b1a2e21b9589");
  }

  @Test
  void preservesWholeSlipVoidSchema() {
    assertThat(sha256("V4__bet_void_reason.sql"))
        .isEqualTo("4e42907201bdbfe211f505ac9a8fbe4321a493b55a56747f48af078eb98f3ca8");
  }

  @Test
  void preservesPlacementRecoverySchema() {
    assertThat(sha256("V5__placement_recovery.sql"))
        .isEqualTo("298993830f41a22825ae3c0a0ee1cd746520f97308515322d8ed496c4cf71a7d");
  }

  @Test
  void preservesCompensationVerdictSchema() {
    assertThat(sha256("V6__placement_compensation_and_verdict.sql"))
        .isEqualTo("1625d9d3140aa8f1888bd8b61dd9d2d00ba612d9c2c5ce35b8d15620368e8e67");
  }

  @Test
  void requiresCanonicalPersistedRiskToken() {
    assertThat(migrationText("V7__risk_reservation_token.sql"))
        .contains("risk_reservation_token VARCHAR(64)")
        .contains("^[0-9a-f]{64}$");
  }

  @Test
  void deduplicatesWalletHintsByEventHeader() {
    assertThat(migrationText("V8__wallet_event_reconciliation.sql"))
        .contains("event_id        UUID                     PRIMARY KEY")
        .contains("payload_sha256 ~ '^[0-9a-f]{64}$'")
        .contains("WHERE processed_at IS NULL");
  }

  private String migrationText(String migration) {
    try (InputStream input = getClass().getResourceAsStream("/db/migration/" + migration)) {
      if (input == null) {
        throw new IllegalStateException("Missing migration " + migration);
      }
      return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String sha256(String migration) {
    try (InputStream input = getClass().getResourceAsStream("/db/migration/" + migration)) {
      if (input == null) {
        throw new IllegalStateException("Missing migration " + migration);
      }
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
    } catch (IOException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
