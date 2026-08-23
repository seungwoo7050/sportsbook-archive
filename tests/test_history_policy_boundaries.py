import unittest

from scripts.cold_gate.history_policy import verify_history
from scripts.cold_gate.history_repository import Change
from scripts.cold_gate.history_rules import ROOT_SUBJECT
from tests.history_policy_fixture import changed, history, valid_development, valid_release


class HistoryPolicyBoundaryTest(unittest.TestCase):
    def test_rejects_mixed_or_mislabeled_change_boundaries(self):
        valid = valid_development()
        invalid = (
            changed(
                valid,
                1,
                changes=(Change("src/app.py", 1, 0), Change("tests/test_app.py", 1, 0)),
            ),
            changed(valid, 1, message="test(app): add behavior"),
            changed(valid, 2, message="build(app): verify behavior"),
            changed(valid, 1, changes=()),
        )
        for commits in invalid:
            with self.subTest(subject=commits[1].subject):
                with self.assertRaises(RuntimeError):
                    verify_history(commits, minimum=1)

    def test_limits_production_files_and_requires_adjacent_tests(self):
        valid = valid_development()
        too_many = changed(
            valid,
            1,
            changes=tuple(Change(f"src/{name}.py", 1, 0) for name in "abc"),
        )
        unpaired = history(
            (ROOT_SUBJECT, ("README.md",)),
            ("build(app): add first behavior", ("src/first.py",)),
            ("build(app): add second behavior", ("src/second.py",)),
            ("test(app): verify behavior", ("tests/test_app.py",)),
        )
        with self.assertRaisesRegex(RuntimeError, "two production"):
            verify_history(too_many, minimum=1)
        with self.assertRaisesRegex(RuntimeError, "adjacent test"):
            verify_history(unpaired, minimum=1)

    def test_enforces_churn_with_single_file_release_exceptions(self):
        valid = valid_development()
        oversized = changed(valid, 1, changes=(Change("src/app.py", 101, 0),))
        pom = changed(valid, 1, changes=(Change("pom.xml", 500, 0),))
        final_docs = valid_release()
        final_docs = changed(final_docs, -1, changes=(Change("README.md", 500, 0),))
        with self.assertRaisesRegex(RuntimeError, "100-line"):
            verify_history(oversized, minimum=1)
        verify_history(pom, minimum=1)
        verify_history(final_docs, minimum=1)

    def test_rejects_intermediate_documents_and_tracked_outputs(self):
        valid = valid_development()
        invalid_paths = (
            "docs/operations",
            "handoffs/wave4/integration.md",
            "evidence/cold-gate/run.tsv",
            "fixtures/tool/target/tool.jar",
            "notes/devlog.md",
        )
        for path in invalid_paths:
            with self.subTest(path=path):
                commits = changed(valid, 1, changes=(Change(path, 1, 0),))
                with self.assertRaises(RuntimeError):
                    verify_history(commits, minimum=1)

    def test_rejects_duplicate_paths(self):
        valid = valid_development()
        duplicate = changed(
            valid,
            2,
            changes=(Change("tests/test_app.py", 1, 0), Change("tests/test_app.py", 1, 0)),
        )
        with self.assertRaisesRegex(RuntimeError, "duplicate"):
            verify_history(duplicate, minimum=1)


if __name__ == "__main__":
    unittest.main()
