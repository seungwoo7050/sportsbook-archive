package com.sportsbook.admin.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminDocumentationHistoryTest extends HistoryGuardFixture {

  @Test
  void permitsOneLargeFinalReadmeAfterRelease() throws Exception {
    initialize();
    release();
    write("README.md", "admin API contract\n".repeat(120));
    commit("docs(project): document admin API contracts", "README.md");

    assertThat(guard().code()).isZero();
  }

  @Test
  void rejectsFinalDocumentationWithoutRelease() throws Exception {
    initialize();
    write("README.md", "admin API contract\n".repeat(120));
    commit("docs(project): document admin API contracts", "README.md");

    Result result = guard();

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("release commit is not immediately before");
  }

  @Test
  void rejectsAdditionalFinalDocumentationPaths() throws Exception {
    initialize();
    release();
    write("README.md", "admin API contract\n".repeat(120));
    write("notes.md", "extra documentation\n");
    commit("docs(project): document admin API contracts", "README.md", "notes.md");

    Result result = guard();

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("intermediate documentation commit");
  }

  @Test
  void rejectsReleaseWithoutFinalDocumentation() throws Exception {
    initialize();
    release();

    Result result = guard();

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("release commit exists without final documentation");
  }

  @Test
  void rejectsIntermediateDocumentation() throws Exception {
    initialize();
    write("notes.md", "intermediate\n");
    commit("docs(project): describe progress", "notes.md");

    Result result = guard();

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("intermediate documentation commit");
  }

  private void release() throws Exception {
    write("pom.xml", "<project><version>1.0.0</version></project>\n");
    commit("chore(release): release admin API 1.0.0", "pom.xml");
  }
}
