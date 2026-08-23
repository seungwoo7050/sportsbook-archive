package com.sportsbook.admin.ops;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminHistoryGuardPolicyTest extends HistoryGuardFixture {

  private static final int LONG_HISTORY_LENGTH = 240;

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

  @Test
  void traversesLongHistoriesWithoutPipeTermination() throws Exception {
    initialize();
    write("src/main/java/Feature.java", "class Feature {}\n");
    commit("feat(core): add feature", "src/main/java/Feature.java");
    write("src/test/java/FeatureTest.java", "class FeatureTest {}\n");
    commit("test(core): verify feature", "src/test/java/FeatureTest.java");
    for (int index = 0; index < LONG_HISTORY_LENGTH; index++) {
      write("marker.txt", index + "\n");
      commit("chore(project): extend history " + index, "marker.txt");
    }

    assertThat(guard().code()).isZero();
  }
}
