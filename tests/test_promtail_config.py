import pathlib
import subprocess
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "observability/promtail/promtail.yml"


class PromtailConfigTest(unittest.TestCase):
    def test_validates_dynamic_project_scoping_without_label_overwrite(self) -> None:
        checked = subprocess.run(
            [
                "docker",
                "run",
                "--rm",
                "--network",
                "none",
                "--env",
                "COMPOSE_PROJECT_NAME=sportsbook-contract",
                "--volume",
                f"{CONFIG}:/etc/promtail/promtail.yml:ro",
                "grafana/promtail:3.1.1",
                "-check-syntax",
                "-config.file=/etc/promtail/promtail.yml",
                "-config.expand-env=true",
            ],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(checked.returncode, 0, checked.stderr)

        config = CONFIG.read_text()
        self.assertIn("regex: ${COMPOSE_PROJECT_NAME}", config)
        self.assertIn("target_label: project", config)
        self.assertIn("target_label: service", config)
        self.assertIn("/var/lib/docker/containers/$1/*-json.log", config)
        self.assertNotIn("regex: sportsbook", config)
        self.assertNotIn("- template:", config)


if __name__ == "__main__":
    unittest.main()
