package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AuditV1ChecksumTest {

  private static final String APPROVED_SHA256 =
      "42dcf9a83473dfe3ba74efce702e7ce5014dea45f25ff2b7699134e1aac360a7";

  @Test
  void preservesTheReleasedV1MigrationByteForByte() throws Exception {
    byte[] migration =
        Files.readAllBytes(Path.of("src/main/resources/db/migration/V1__audit_log.sql"));
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(migration);

    assertThat(HexFormat.of().formatHex(digest)).isEqualTo(APPROVED_SHA256);
  }
}
