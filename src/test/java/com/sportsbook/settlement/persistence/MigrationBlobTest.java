package com.sportsbook.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class MigrationBlobTest {

  private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

  @Test
  void preservesV1ReadModelBlobAndConstraints() throws IOException {
    Path migration = MIGRATIONS.resolve("V1__bet_read_model.sql");

    assertThat(gitBlobOid(migration)).isEqualTo("9bfcc888e815563b1337f00f435e47870db4205a");
    assertThat(Files.readString(migration))
        .contains("PRIMARY KEY", "UNIQUE (bet_id, leg_index)", "WHERE status = 'PENDING'");
  }

  @Test
  void preservesV3OutboxBlobAndUnpublishedIndex() throws IOException {
    Path migration = MIGRATIONS.resolve("V3__outbox.sql");

    assertThat(gitBlobOid(migration)).isEqualTo("a9b136b813195a6680895376877f69c2049ab19b");
    assertThat(Files.readString(migration))
        .contains("CREATE TABLE outbox_event", "WHERE published_at IS NULL");
  }

  private static String gitBlobOid(Path path) throws IOException {
    byte[] content = Files.readAllBytes(path);
    byte[] header = ("blob " + content.length + "\0").getBytes(StandardCharsets.UTF_8);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      digest.update(header);
      return java.util.HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK must provide SHA-1 for Git compatibility", exception);
    }
  }
}
