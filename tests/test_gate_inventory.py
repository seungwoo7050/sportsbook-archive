import json
import os
import pathlib
import subprocess
import unittest

from scripts.cold_gate.inventory import (
    APPLICATION_SERVICES,
    COMPLETED_SERVICES,
    LONG_RUNNING_SERVICES,
    MIGRATION_VERSIONS,
    SERVICES,
)


ROOT = pathlib.Path(__file__).resolve().parents[1]


class GateInventoryTest(unittest.TestCase):
    def test_matches_the_combined_compose_service_set(self) -> None:
        environment = os.environ.copy()
        environment["COMPOSE_PROJECT_NAME"] = "sportsbook-inventory"
        result = subprocess.run(
            [
                "docker", "compose", "--project-name", "sportsbook-inventory",
                "--file", str(ROOT / "compose.yaml"),
                "--file", str(ROOT / "compose.toxiproxy.yaml"),
                "--file", str(ROOT / "compose.observability.yaml"),
                "config", "--no-interpolate", "--format", "json",
            ],
            cwd=ROOT,
            env=environment,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(set(json.loads(result.stdout)["services"]), set(SERVICES))

    def test_partitions_runtime_roles_and_exact_migrations(self) -> None:
        self.assertEqual(len(SERVICES), 21)
        self.assertEqual(len(LONG_RUNNING_SERVICES), 18)
        self.assertEqual(len(COMPLETED_SERVICES), 3)
        self.assertEqual(LONG_RUNNING_SERVICES | COMPLETED_SERVICES, set(SERVICES))
        self.assertEqual(len(APPLICATION_SERVICES), 7)
        self.assertEqual(
            MIGRATION_VERSIONS,
            {
                "wallet": ("1", "2", "3", "4"),
                "betting": tuple(str(value) for value in range(1, 11)),
                "settlement": ("1", "3", "4", "5", "6", "7", "8", "9", "10"),
                "admin": ("1", "2"),
            },
        )


if __name__ == "__main__":
    unittest.main()
