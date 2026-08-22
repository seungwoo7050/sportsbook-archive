package com.sportsbook.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettlementDocumentationHistoryTest {

  private static final Path GUARD = Path.of(".github/scripts/verify-history.sh").toAbsolutePath();

  @TempDir Path repository;

  @Test
  void permitsOneLargeReadmeOnlyProjectCommit() throws Exception {
    initialize();
    Files.writeString(repository.resolve("README.md"), "settlement docs\n".repeat(120));
    commit("README.md");

    assertThat(run("bash", GUARD.toString()).code()).isZero();
  }

  @Test
  void rejectsTheDocumentationExceptionForAdditionalPaths() throws Exception {
    initialize();
    Files.writeString(repository.resolve("README.md"), "settlement docs\n".repeat(120));
    Files.writeString(repository.resolve("notes.md"), "not final documentation\n");
    commit("README.md", "notes.md");

    Result result = run("bash", GUARD.toString());

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("exceeds the 100-line review gate");
  }

  @Test
  void rejectsDevelopmentLogWordsAnywhereInTheSubject() throws Exception {
    initialize();
    Files.writeString(repository.resolve("README.md"), "small readme\n");
    commitWithSubject("docs(project): add devlog notes", "README.md");

    Result result = run("bash", GUARD.toString());

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("forbidden development-log material");
  }

  @Test
  void rejectsTheLargeDocumentationCommitWhenItIsNotHead() throws Exception {
    initialize();
    Files.writeString(repository.resolve("README.md"), "settlement docs\n".repeat(120));
    commit("README.md");
    Files.writeString(repository.resolve("marker.txt"), "later commit\n");
    commitWithSubject("test(ci): add follow-up marker", "marker.txt");

    Result result = run("bash", GUARD.toString());

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("exceeds the 100-line review gate");
  }

  private void initialize() throws Exception {
    assertThat(run("git", "init", "-q").code()).isZero();
    assertThat(run("git", "config", "user.name", "Settlement CI").code()).isZero();
    assertThat(run("git", "config", "user.email", "settlement@example.invalid").code()).isZero();
  }

  private void commit(String... paths) throws Exception {
    commitWithSubject("docs(project): document settlement service", paths);
  }

  private void commitWithSubject(String subject, String... paths) throws Exception {
    List<String> add = new ArrayList<>(List.of("git", "add"));
    add.addAll(Arrays.asList(paths));
    assertThat(run(add.toArray(String[]::new)).code()).isZero();
    assertThat(run("git", "commit", "-q", "-m", subject).code()).isZero();
  }

  private Result run(String... command) throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder(command)
            .directory(repository.toFile())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Result(process.waitFor(), output);
  }

  private record Result(int code, String output) {}
}
