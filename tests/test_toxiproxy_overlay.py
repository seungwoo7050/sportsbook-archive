import json
import pathlib
import subprocess

from tests.compose_contract_fixture import ComposeContractFixture


ROOT = pathlib.Path(__file__).resolve().parents[1]
OVERLAY = ROOT / "compose.toxiproxy.yaml"


class ToxiproxyOverlayTest(ComposeContractFixture):
    def test_overrides_only_three_canonical_http_dependencies(self) -> None:
        result = subprocess.run(
            [
                "docker",
                "compose",
                "--project-name",
                self.project,
                "--file",
                str(ROOT / "compose.yaml"),
                "--file",
                str(OVERLAY),
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
        services = json.loads(result.stdout)["services"]

        self.assertEqual(
            services["betting"]["environment"]["RISK_BASE_URL"],
            "http://toxiproxy:18083",
        )
        self.assertEqual(
            services["betting"]["environment"]["WALLET_BASE_URL"],
            "http://toxiproxy:18081",
        )
        settlement = services["settlement"]["environment"]
        self.assertEqual(
            settlement["SETTLEMENT_WALLET_BASE_URL"], "http://toxiproxy:28081"
        )
        self.assertNotIn("WALLET_BASE_URL", settlement)
        admin_port = services["toxiproxy"]["ports"][0]
        self.assertEqual(admin_port["host_ip"], "127.0.0.1")
        self.assertEqual(admin_port["target"], 8474)
        self.assertNotIn("published", admin_port)
        self.assertEqual(set(services["toxiproxy"]["networks"]), {"backend"})
        self.assertEqual(
            services["betting"]["depends_on"]["toxiproxy"]["condition"],
            "service_healthy",
        )
        self.assertEqual(
            services["settlement"]["depends_on"]["toxiproxy"]["condition"],
            "service_healthy",
        )

        proxies = json.loads((ROOT / "chaos/toxiproxy.json").read_text())
        self.assertEqual(
            {(proxy["name"], proxy["listen"], proxy["upstream"]) for proxy in proxies},
            {
                ("betting_to_risk", "0.0.0.0:18083", "risk:8083"),
                ("betting_to_wallet", "0.0.0.0:18081", "wallet:8081"),
                ("settlement_to_wallet", "0.0.0.0:28081", "wallet:8081"),
            },
        )
        self.assertTrue(all(proxy["enabled"] for proxy in proxies))
        self.assertNotIn("kafka", json.dumps(proxies).lower())


if __name__ == "__main__":
    import unittest

    unittest.main()
