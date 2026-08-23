from tests.compose_contract_fixture import ComposeContractFixture


class NetworkExposureTest(ComposeContractFixture):
    def test_exposes_only_one_loopback_gateway_on_an_internal_network(self) -> None:
        self.environment["GATEWAY_HOST_PORT"] = "18080"
        rendered = self.rendered()
        services = rendered["services"]

        published = {name for name, service in services.items() if service.get("ports")}
        self.assertEqual(published, {"gateway"})
        self.assertEqual(
            services["gateway"]["ports"],
            [
                {
                    "mode": "ingress",
                    "host_ip": "127.0.0.1",
                    "target": 8080,
                    "published": "18080",
                    "protocol": "tcp",
                }
            ],
        )
        self.assertEqual(set(rendered["networks"]), {"backend"})
        self.assertTrue(rendered["networks"]["backend"]["internal"])

        for name, service in services.items():
            with self.subTest(service=name):
                self.assertNotEqual(service.get("network_mode"), "host")
                if name == "secret-preflight":
                    self.assertEqual(service["network_mode"], "none")
                else:
                    self.assertEqual(set(service["networks"]), {"backend"})


if __name__ == "__main__":
    import unittest

    unittest.main()
