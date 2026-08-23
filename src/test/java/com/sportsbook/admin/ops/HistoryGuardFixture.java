package com.sportsbook.admin.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

abstract class HistoryGuardFixture {

  private static final Path GUARD = Path.of(".github/scripts/verify-history.sh").toAbsolutePath();

  @TempDir Path repository;

  void initialize() throws Exception {
    assertThat(run("git", "init", "-q").code()).isZero();
    assertThat(run("git", "config", "user.name", "Admin CI").code()).isZero();
    assertThat(run("git", "config", "user.email", "admin@example.invalid").code()).isZero();
    write("README.md", "# Admin API\n");
    commit("docs(project): establish admin API ownership", "README.md");
  }

  void write(String path, String content) throws IOException {
    Path target = repository.resolve(path);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }

  void commit(String subject, String... paths) throws Exception {
    List<String> add = new ArrayList<>(List.of("git", "add"));
    add.addAll(Arrays.asList(paths));
    assertThat(run(add.toArray(String[]::new)).code()).isZero();
    assertThat(run("git", "commit", "-q", "-m", subject).code()).isZero();
  }

  Result guard() throws Exception {
    return run("bash", GUARD.toString());
  }

  Result run(String... command) throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder(command)
            .directory(repository.toFile())
            .redirectErrorStream(true)
            .start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Result(process.waitFor(), output);
  }

  record Result(int code, String output) {}
}
