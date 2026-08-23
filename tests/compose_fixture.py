import os
import pathlib
import subprocess
import unittest
import uuid


ROOT = pathlib.Path(__file__).resolve().parents[1]
COMPOSE_FILE = ROOT / "compose.yaml"


class ComposeFixture(unittest.TestCase):
    def setUp(self) -> None:
        self.project = f"sportsbook-test-{uuid.uuid4().hex[:12]}"
        self.environment = os.environ.copy()
        self.environment["POSTGRES_PASSWORD"] = "postgres-contract-password"
        self.addCleanup(self.cleanup_project)

    def compose(
        self, *arguments: str, environment: dict[str, str] | None = None
    ) -> subprocess.CompletedProcess[str]:
        run_environment = self.environment | (environment or {})
        return subprocess.run(
            [
                "docker",
                "compose",
                "--project-name",
                self.project,
                "--file",
                str(COMPOSE_FILE),
                *arguments,
            ],
            cwd=ROOT,
            env=run_environment,
            text=True,
            capture_output=True,
            check=False,
        )

    def cleanup_project(self) -> None:
        subprocess.run(
            [
                "docker",
                "compose",
                "--project-name",
                self.project,
                "--file",
                str(COMPOSE_FILE),
                "down",
                "--volumes",
                "--remove-orphans",
                "--timeout",
                "5",
            ],
            cwd=ROOT,
            env=self.environment,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
