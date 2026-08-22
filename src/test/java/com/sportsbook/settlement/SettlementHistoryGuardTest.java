package com.sportsbook.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SettlementHistoryGuardTest {

  @Test
  void rejectsArchiveHistoryRuleViolations() throws Exception {
    String workflow = Files.readString(Path.of(".github/workflows/settlement-ci.yml"));
    String guard = Files.readString(Path.of(".github/scripts/verify-history.sh"));

    assertThat(workflow).contains("fetch-depth: 0", "bash .github/scripts/verify-history.sh");
    assertThat(guard)
        .contains(
            "subject_pattern=",
            "non-empty commit body",
            "forbidden development-log material",
            "mixes production and test files",
            "archive history contains a merge commit",
            "100-line review gate");
    assertGuardPasses();
  }

  private static void assertGuardPasses() throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder("bash", ".github/scripts/verify-history.sh")
            .redirectErrorStream(true)
            .start();
    assertThat(process.waitFor(1, TimeUnit.MINUTES)).isTrue();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.exitValue()).describedAs(output).isZero();
  }
}
