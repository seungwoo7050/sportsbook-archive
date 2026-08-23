import unittest

from scripts.cold_gate.history_policy import verify_history
from scripts.cold_gate.history_repository import Change
from scripts.cold_gate.history_rules import ROOT_SUBJECT
from tests.history_policy_fixture import changed, valid_development, valid_release


class HistoryPolicyStructureTest(unittest.TestCase):
    def test_accepts_complete_prerelease_and_terminal_release_shapes(self):
        verify_history(valid_development(), minimum=1)
        verify_history(valid_release(), minimum=1)

    def test_requires_full_linear_history_and_exact_root(self):
        valid = valid_development()
        invalid = (
            (changed(valid, 0, message="docs(project): wrong root"), "root"),
            (changed(valid, 0, changes=(Change("OTHER", 1, 0),)), "root"),
            (changed(valid, 1, parents=(valid[0].sha, "f" * 40)), "linear"),
        )
        for commits, message in invalid:
            with self.subTest(message=message):
                with self.assertRaisesRegex(RuntimeError, message):
                    verify_history(commits, minimum=1)
        with self.assertRaisesRegex(RuntimeError, "only 3 commits"):
            verify_history(valid, minimum=4)

    def test_rejects_message_bodies_and_process_markers(self):
        valid = valid_development()
        invalid = (
            changed(valid, 1, message="build(app): add behavior\n\nimplementation note"),
            changed(valid, 1, message="not conventional"),
            changed(valid, 1, message="fix(app): squash temporary work"),
            changed(valid, 1, message="fix(app): record provenance"),
        )
        for commits in invalid:
            with self.subTest(subject=commits[1].subject):
                with self.assertRaises(RuntimeError):
                    verify_history(commits, minimum=1)

    def test_requires_exact_terminal_release_pair(self):
        release = valid_release()
        invalid = (
            release[:-1],
            changed(release, -2, changes=(Change("version.txt", 1, 0),)),
            changed(release, -1, changes=(Change("docs/README.md", 1, 0),)),
        )
        for commits in invalid:
            with self.subTest(length=len(commits)):
                with self.assertRaisesRegex(RuntimeError, "release|terminal"):
                    verify_history(commits, minimum=1)

    def test_root_constant_remains_the_owned_document_subject(self):
        self.assertEqual(ROOT_SUBJECT, "docs(project): establish orchestration ownership")


if __name__ == "__main__":
    unittest.main()
