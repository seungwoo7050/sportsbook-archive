import os
import pathlib
import subprocess
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "docker/kafka/wait-consumer-assignments.sh"
FAKE = ROOT / "tests/fake_consumer_groups.py"


class ConsumerAssignmentGateTest(unittest.TestCase):
    def run_gate(self, mode: str) -> subprocess.CompletedProcess[str]:
        environment = os.environ.copy()
        environment.update(
            {
                "KAFKA_CONSUMER_GROUPS": str(FAKE),
                "ASSIGNMENT_MODE": mode,
                "ASSIGNMENT_TIMEOUT_SECONDS": "1",
                "ASSIGNMENT_POLL_SECONDS": "0.01",
            }
        )
        return subprocess.run(
            [str(SCRIPT)],
            cwd=ROOT,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_accepts_both_exact_active_nine_partition_assignments(self) -> None:
        result = self.run_gate("ready")

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(
            result.stdout.strip(),
            "consumer-assignment: gateway-bets=9 betting-resolution=9",
        )

    def test_rejects_incomplete_inactive_or_extra_assignments(self) -> None:
        for mode in ("missing", "inactive", "extra"):
            with self.subTest(mode=mode):
                result = self.run_gate(mode)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("timed out", result.stderr)


if __name__ == "__main__":
    unittest.main()
