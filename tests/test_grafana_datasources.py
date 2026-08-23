import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
CONFIG = ROOT / "observability/grafana/provisioning/datasources/datasources.yml"


class GrafanaDatasourceTest(unittest.TestCase):
    def test_provisions_only_internal_prometheus_and_loki_origins(self) -> None:
        config = CONFIG.read_text()

        self.assertEqual(config.count("  - name: Prometheus\n"), 2)
        self.assertEqual(config.count("  - name: Loki\n"), 2)
        self.assertEqual(config.count("uid: prometheus"), 1)
        self.assertEqual(config.count("uid: loki"), 1)
        self.assertIn("url: http://prometheus:9090", config)
        self.assertIn("url: http://loki:3100", config)
        self.assertEqual(config.count("isDefault: true"), 1)
        self.assertNotIn("localhost", config)
        self.assertNotIn("password", config.lower())


if __name__ == "__main__":
    unittest.main()
