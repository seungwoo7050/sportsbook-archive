import io
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout

import scripts.history_guard as command
from scripts.cold_gate.history_policy import verify_history
from scripts.cold_gate.history_repository import load_history
from tests.history_guard_fixture import create_long_history


ROOT = pathlib.Path(__file__).resolve().parents[1]


class HistoryGuardTest(unittest.TestCase):
    def test_verifies_the_complete_current_archive_history(self):
        commits = load_history(ROOT)

        verify_history(commits)

        self.assertGreaterEqual(len(commits), 240)
        self.assertEqual(commits[-1].sha, command.load_history(ROOT)[-1].sha)

    def test_finds_a_violation_older_than_the_latest_240_commits(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            create_long_history(root, pairs=121, fault_pair=1)

            commits = load_history(root)

            self.assertEqual(len(commits), 243)
            with self.assertRaisesRegex(RuntimeError, "100-line"):
                verify_history(commits)

    def test_command_reports_the_verified_commit_count(self):
        output = io.StringIO()
        with redirect_stdout(output):
            command.main()

        count = len(load_history(ROOT))
        self.assertEqual(output.getvalue(), f"history_guard_commits={count}\n")

    def test_direct_archive_scripts_bootstrap_without_pythonpath(self):
        environment = os.environ.copy()
        environment.pop("PYTHONPATH", None)
        for name in ("history_guard.py", "cold_release_gate.py"):
            script = ROOT / "scripts" / name
            expression = f"import runpy; runpy.run_path({str(script)!r})"
            with self.subTest(name=name):
                subprocess.run(
                    [sys.executable, "-I", "-B", "-c", expression],
                    cwd=ROOT.parent,
                    env=environment,
                    text=True,
                    capture_output=True,
                    check=True,
                )


if __name__ == "__main__":
    unittest.main()
