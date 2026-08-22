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

  private String sha256(String migration) {
    try (InputStream input =
        getClass().getResourceAsStream("/db/migration/" + migration)) {
      if (input == null) {
        throw new IllegalStateException("Missing migration " + migration);
      }
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
    } catch (IOException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
