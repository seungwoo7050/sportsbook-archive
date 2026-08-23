import pathlib
import re
import subprocess
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "observability/prometheus/prometheus.yml"


class PrometheusConfigTest(unittest.TestCase):
    def test_is_valid_and_scrapes_the_exact_service_endpoints(self) -> None:
        checked = subprocess.run(
            [
                "docker",
                "run",
                "--rm",
                "--network",
                "none",
                "--volume",
                f"{CONFIG}:/etc/prometheus/prometheus.yml:ro",
                "--entrypoint",
                "promtool",
                "prom/prometheus:v2.54.1",
                "check",
                "config",
                "/etc/prometheus/prometheus.yml",
            ],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(checked.returncode, 0, checked.stderr)

        targets = set(re.findall(r'targets: \["([^\"]+)"\]', CONFIG.read_text()))
        self.assertEqual(
            targets - {"localhost:9090"},
            {
                "gateway:8080",
                "wallet:8081",
                "betting:8082",
                "risk:8083",
                "settlement:8084",
                "odds:8085",
                "admin:8090",
            },
        )


if __name__ == "__main__":
    unittest.main()
