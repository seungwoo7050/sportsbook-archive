package com.sportsbook.admin.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuditLoadFixtureTest {

  private static final Path ROOT = Path.of(System.getProperty("user.dir"));

  @Test
  void exercisesTheAuthenticatedAuditReadWithoutPersistingEvidence() throws Exception {
    String script = Files.readString(ROOT.resolve("load-test/scenarios/audit-read.js"));

    assertThat(script)
        .contains(
            "http.get(`${BASE_URL}/admin/v1/audit-logs?size=20`",
            "Authorization: `Bearer ${TOKEN}`",
            "http_req_failed",
            "http_req_duration")
        .doesNotContain(
            "handleSummary", "summary-export", "load-test/results", "writeFile", "appendFile");
  }

  @Test
  void keepsAllLoadEvidenceIgnored() throws Exception {
    String ignores = Files.readString(ROOT.resolve(".gitignore"));

    assertThat(ignores).contains("load-test/results/");
    assertThat(ROOT.resolve("load-test/README.md")).doesNotExist();
  }
}
