import io
import pathlib
import subprocess
import unittest
from contextlib import redirect_stdout
from unittest import mock

import scripts.cold_release_gate as subject


SHA = "0123456789abcdef0123456789abcdef01234567"


class ColdReleaseCommandTest(unittest.TestCase):
    def test_reads_one_clean_exact_head(self):
        runner = mock.Mock(
            side_effect=(
                subprocess.CompletedProcess([], 0, stdout=SHA + "\n"),
                subprocess.CompletedProcess([], 0, stdout=""),
            )
        )
        with mock.patch.object(subject.subprocess, "run", runner):
            observed = subject.clean_head(pathlib.Path("/release"))

        self.assertEqual(observed, SHA)
        self.assertEqual(runner.call_count, 2)
        self.assertEqual(runner.call_args_list[1].args[0][-2:], ["--porcelain", "--untracked-files=no"])

    def test_rejects_dirty_or_malformed_checkout(self):
        for head, dirty in ((SHA, " M compose.yaml\n"), ("short", "")):
            with self.subTest(head=head), mock.patch.object(
                subject.subprocess,
                "run",
                side_effect=(
                    subprocess.CompletedProcess([], 0, stdout=head + "\n"),
                    subprocess.CompletedProcess([], 0, stdout=dirty),
                ),
            ):
                with self.assertRaisesRegex(RuntimeError, "clean exact HEAD"):
                    subject.clean_head(pathlib.Path("/release"))

    def test_runs_gate_for_script_root_and_prints_evidence_path(self):
        output = io.StringIO()
        with mock.patch.object(subject, "clean_head", return_value=SHA) as head, mock.patch.object(
            subject, "run_release_gate", return_value=pathlib.Path("/evidence/run")
        ) as gate, redirect_stdout(output):
            subject.main()

        root = pathlib.Path(subject.__file__).resolve().parents[1]
        head.assert_called_once_with(root)
        gate.assert_called_once_with(root, SHA)
        self.assertEqual(output.getvalue(), "cold_gate_evidence=/evidence/run\n")


if __name__ == "__main__":
    unittest.main()
