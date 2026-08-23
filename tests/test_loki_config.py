import pathlib
import subprocess
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "observability/loki/loki.yml"


class LokiConfigTest(unittest.TestCase):
    def test_validates_single_node_storage_and_bounded_retention(self) -> None:
        checked = subprocess.run(
            [
                "docker",
                "run",
                "--rm",
                "--network",
                "none",
                "--volume",
                f"{CONFIG}:/etc/loki/loki.yml:ro",
                "grafana/loki:3.1.1",
                "-verify-config=true",
                "-config.file=/etc/loki/loki.yml",
            ],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(checked.returncode, 0, checked.stderr)
        config = CONFIG.read_text()
        self.assertIn("replication_factor: 1", config)
        self.assertIn("retention_period: 72h", config)
        self.assertIn("retention_enabled: true", config)
        self.assertNotIn("s3", config.lower())


if __name__ == "__main__":
    unittest.main()
