import unittest

from scripts.cold_gate.history_repository import Change
from scripts.cold_gate.history_rules import (
    forbidden_path,
    is_documentation,
    is_large_exception,
    is_test_path,
)


class HistoryRulesTest(unittest.TestCase):
    def test_classifies_only_owned_test_boundaries(self):
        accepted = (
            "tests/test_gate.py",
            "e2e/scenario_01.py",
            "fixtures/avro-publisher/src/test/java/PublisherTest.java",
        )
        rejected = (
            "scripts/cold_gate/gate.py",
            "fixtures/avro-publisher/src/main/java/Publisher.java",
            "compose.yaml",
        )
        self.assertTrue(all(is_test_path(path) for path in accepted))
        self.assertFalse(any(is_test_path(path) for path in rejected))

    def test_detects_documentation_and_generated_or_process_files(self):
        self.assertTrue(is_documentation("README.md"))
        self.assertFalse(is_documentation("config/required-secrets.txt"))
        forbidden = (
            ".runtime/run/owner",
            "evidence/cold-gate/run.tsv",
            "fixtures/tool/target/tool.jar",
            "docker/.jars/generation/app.jar",
            "docker/jars",
            "notes/devlog.md",
            "src/App.class",
            "logs/runtime.log",
        )
        self.assertTrue(all(forbidden_path(path) for path in forbidden))
        self.assertFalse(forbidden_path("scripts/cold_gate/log_evidence.py"))

    def test_allows_only_one_responsibility_large_exceptions(self):
        allowed = (
            "mvnw",
            "service/pom.xml",
            "src/main/resources/db/migration/V2__lifecycle.sql",
            "src/main/avro/AdminAction.avsc",
        )
        for path in allowed:
            with self.subTest(path=path):
                self.assertTrue(is_large_exception((Change(path, 101, 0),)))
        self.assertFalse(
            is_large_exception(
                (Change("pom.xml", 101, 0), Change("src/App.java", 1, 0))
            )
        )
        self.assertFalse(is_large_exception((Change("src/App.java", 101, 0),)))


if __name__ == "__main__":
    unittest.main()
