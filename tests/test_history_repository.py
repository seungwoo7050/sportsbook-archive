import pathlib
import subprocess
import unittest

from scripts.cold_gate.history_repository import FORMAT, load_history


ROOT_SHA = "a" * 40
TEST_SHA = "b" * 40


class HistoryRepositoryTest(unittest.TestCase):
    def test_parses_ordered_messages_parents_and_measured_changes(self):
        output = (
            f"\x1e{ROOT_SHA}\x1f\x1fdocs(project): root\n\x1f\n3\t0\tREADME.md\n"
            f"\x1e{TEST_SHA}\x1f{ROOT_SHA}\x1ftest(repo): verify root\n\x1f\n"
            "4\t1\ttests/test_root.py\n"
        )

        def runner(command, **_options):
            self.assertIn(f"--format={FORMAT}", command)
            self.assertIn("--no-renames", command)
            return subprocess.CompletedProcess(command, 0, stdout=output)

        commits = load_history(pathlib.Path("/release"), runner=runner)

        self.assertEqual([commit.sha for commit in commits], [ROOT_SHA, TEST_SHA])
        self.assertEqual(commits[0].parents, ())
        self.assertEqual(commits[1].parents, (ROOT_SHA,))
        self.assertEqual(commits[0].subject, "docs(project): root")
        self.assertEqual(commits[1].changes[0].path, "tests/test_root.py")
        self.assertEqual(commits[1].churn, 5)

    def test_rejects_binary_or_empty_history_receipts(self):
        outputs = (
            "",
            f"\x1e{ROOT_SHA}\x1f\x1fbuild(repo): binary\n\x1f\n-\t-\tasset.bin\n",
        )
        for output in outputs:
            with self.subTest(output=output):
                runner = lambda command, **_options: subprocess.CompletedProcess(
                    command, 0, stdout=output
                )
                with self.assertRaisesRegex(RuntimeError, "empty|unmeasured"):
                    load_history(pathlib.Path("/release"), runner=runner)


if __name__ == "__main__":
    unittest.main()
