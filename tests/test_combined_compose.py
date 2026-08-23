import json
import pathlib
import subprocess

from tests.compose_contract_fixture import ComposeContractFixture


ROOT = pathlib.Path(__file__).resolve().parents[1]


class CombinedComposeTest(ComposeContractFixture):
    def test_renders_secret_independent_canonical_config(self) -> None:
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
                "--no-interpolate",
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
        self.assertEqual(json.loads(result.stdout)["name"], self.project)

    def test_combines_chaos_and_observability_inside_the_unique_project(self) -> None:
        self.environment.update(
            {
                "GATEWAY_HOST_PORT": "18080",
                "GRAFANA_ADMIN_PASSWORD": "grafana-contract-password",
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
        self.assertEqual(set(published), {"gateway", "toxiproxy", "grafana"})
        for ports in published.values():
            self.assertTrue(all(port["host_ip"] == "127.0.0.1" for port in ports))
        self.assertEqual(published["gateway"][0]["published"], "18080")
        self.assertNotIn("published", published["toxiproxy"][0])
        self.assertEqual(published["toxiproxy"][0]["target"], 8474)
        self.assertNotIn("published", published["grafana"][0])
        self.assertEqual(published["grafana"][0]["target"], 3000)
        for name in ("prometheus", "loki", "promtail"):
            self.assertNotIn("ports", services[name])
        self.assertEqual(
            services["prometheus"]["healthcheck"]["test"],
            ["CMD", "wget", "--quiet", "--spider", "http://localhost:9090/-/ready"],
        )
        self.assertEqual(
            services["loki"]["healthcheck"]["test"],
            ["CMD", "wget", "--quiet", "--spider", "http://localhost:3100/ready"],
        )
        self.assertEqual(
            services["promtail"]["healthcheck"]["test"],
            ["CMD", "wget", "--quiet", "--spider", "http://localhost:9080/ready"],
        )
        promtail_mounts = services["promtail"]["volumes"]
        self.assertEqual(
            {mount["target"] for mount in promtail_mounts},
            {"/etc/promtail/promtail.yml", "/var/run/docker.sock"},
        )
        self.assertTrue(all(mount["read_only"] for mount in promtail_mounts))

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
        for logical, volume in rendered["volumes"].items():
            self.assertEqual(volume["name"], f"{self.project}_{logical}")
        source = "\n".join(
            path.read_text()
            for path in (ROOT / "compose.toxiproxy.yaml", ROOT / "compose.observability.yaml")
        )
        self.assertNotIn("sportsbook_default", source)
        self.assertNotIn("external: true", source)
        self.assertNotIn("${COMPOSE_PROJECT_NAME:-sportsbook}", source)


if __name__ == "__main__":
    import unittest

    unittest.main()
