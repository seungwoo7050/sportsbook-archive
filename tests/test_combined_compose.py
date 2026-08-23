import json
import pathlib
import subprocess

from tests.compose_contract_fixture import ComposeContractFixture


ROOT = pathlib.Path(__file__).resolve().parents[1]


class CombinedComposeTest(ComposeContractFixture):
    def test_combines_chaos_and_observability_inside_the_unique_project(self) -> None:
        self.environment.update(
            {
                "GATEWAY_HOST_PORT": "18080",
                "TOXIPROXY_HOST_PORT": "18474",
                "PROMETHEUS_HOST_PORT": "19090",
                "GRAFANA_HOST_PORT": "13000",
                "LOKI_HOST_PORT": "13100",
                "GRAFANA_ADMIN_PASSWORD": "grafana-contract-password",
                "COMPOSE_PROJECT_NAME": self.project,
            }
        )
        result = subprocess.run(
            [
                "docker",
                "compose",
                "--project-name",
                self.project,
                "--file",
                str(ROOT / "compose.yaml"),
                "--file",
                str(ROOT / "compose.toxiproxy.yaml"),
                "--file",
                str(ROOT / "compose.observability.yaml"),
                "config",
                "--format",
                "json",
            ],
            cwd=ROOT,
            env=self.environment,
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        rendered = json.loads(result.stdout)
        services = rendered["services"]

        self.assertEqual(rendered["name"], self.project)
        self.assertEqual(rendered["networks"]["backend"]["name"], f"{self.project}_backend")
        self.assertTrue(rendered["networks"]["backend"]["internal"])
        for name in ("prometheus", "loki", "grafana", "promtail", "toxiproxy"):
            self.assertEqual(set(services[name]["networks"]), {"backend"})
        self.assertEqual(
            services["promtail"]["environment"]["COMPOSE_PROJECT_NAME"], self.project
        )

        published = {
            name: service["ports"]
            for name, service in services.items()
            if service.get("ports")
        }
        self.assertEqual(
            set(published), {"gateway", "toxiproxy", "prometheus", "grafana", "loki"}
        )
        for ports in published.values():
            self.assertTrue(all(port["host_ip"] == "127.0.0.1" for port in ports))

        self.assertEqual(
            set(rendered["volumes"]),
            {
                "postgres-data",
                "kafka-data",
                "redis-risk-data",
                "redis-odds-data",
                "redis-wallet-data",
                "redis-gateway-data",
                "prometheus-data",
                "loki-data",
                "grafana-data",
            },
        )
        source = "\n".join(
            path.read_text()
            for path in (ROOT / "compose.toxiproxy.yaml", ROOT / "compose.observability.yaml")
        )
        self.assertNotIn("sportsbook_default", source)
        self.assertNotIn("external: true", source)


if __name__ == "__main__":
    import unittest

    unittest.main()
