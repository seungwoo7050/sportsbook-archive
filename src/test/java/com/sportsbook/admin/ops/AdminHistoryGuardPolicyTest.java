package com.sportsbook.admin.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminHistoryGuardPolicyTest extends HistoryGuardFixture {

  @Test
  void acceptsTheRequiredReadmeOnlyRoot() throws Exception {
    initialize();

    assertThat(guard().code()).isZero();
  }

  @Test
  void rejectsNonConventionalSubjectsAndBodies() throws Exception {
    initialize();
    write("marker.txt", "marker\n");
    assertThat(run("git", "add", "marker.txt").code()).isZero();
    assertThat(run("git", "commit", "-q", "-m", "misc change", "-m", "body").code()).isZero();

    Result result = guard();

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("non-conventional subject", "non-empty commit body");
  }

  @Test
  void rejectsReconstructionMaterial() throws Exception {
    initialize();
    write("devlog.txt", "reconstruction provenance\n");
    commit("docs(project): add reconstruction notes", "devlog.txt");

    Result result = guard();

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("forbidden reconstruction material");
  }

  @Test
  void rejectsProductionAndTestMixing() throws Exception {
    initialize();
    write("src/main/java/Feature.java", "class Feature {}\n");
    write("src/test/java/FeatureTest.java", "class FeatureTest {}\n");
    commit(
        "feat(core): mix feature and test",
        "src/main/java/Feature.java",
        "src/test/java/FeatureTest.java");

    Result result = guard();

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("mixes production and test files");
  }

  @Test
  void rejectsOversizedUnapprovedCommits() throws Exception {
    initialize();
    write("marker.txt", "large\n".repeat(101));
    commit("chore(project): add oversized marker", "marker.txt");

    Result result = guard();

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("100-line review gate");
  }

  @Test
  void requiresAnAdjacentTestAfterProduction() throws Exception {
    initialize();
    write("src/main/java/Feature.java", "class Feature {}\n");
    commit("feat(core): add feature", "src/main/java/Feature.java");

    Result result = guard();

    assertThat(result.code()).isNotZero();
    assertThat(result.output()).contains("not followed by its test commit");
  }
}
