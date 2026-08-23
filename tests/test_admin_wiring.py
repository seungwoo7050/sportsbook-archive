from tests.compose_contract_fixture import ComposeContractFixture, PUBLIC_KEY
from tests.secret_fixture import VALID


class AdminWiringTest(ComposeContractFixture):
    def test_wires_four_isolated_clients_authentication_and_readiness(self) -> None:
        admin = self.service("admin")
        environment = admin["environment"]

        self.assert_runtime_build(admin, "admin.jar")
        self.assertEqual(environment["ADMIN_DB_URL"], "jdbc:postgresql://postgres:5432/admin")
        self.assertEqual(environment["ADMIN_KAFKA_BOOTSTRAP"], "kafka:9092")
        self.assertEqual(environment["ADMIN_JWT_PUBLIC_KEY"], PUBLIC_KEY)
        self.assertEqual(environment["ADMIN_IP_ALLOWLIST"], "127.0.0.1/32,::1/128")
        self.assertEqual(environment["ADMIN_TRUSTED_PROXY_CIDRS"], "")
        self.assertEqual(
            {
                name: value
                for name, value in environment.items()
                if name.endswith("BASE_URL")
            },
            {
                "ADMIN_WALLET_BASE_URL": "http://wallet:8081",
                "ADMIN_RISK_BASE_URL": "http://risk:8083",
                "ADMIN_ODDS_FEED_BASE_URL": "http://odds:8085",
                "ADMIN_SETTLEMENT_BASE_URL": "http://settlement:8084",
            },
        )
        self.assertEqual(
            {
                name: value
                for name, value in environment.items()
                if name.endswith("API_KEY")
            },
            {
                "ADMIN_WALLET_API_KEY": VALID["ADMIN_WALLET_API_KEY"],
                "ADMIN_RISK_API_KEY": VALID["ADMIN_RISK_API_KEY"],
                "ADMIN_ODDS_FEED_API_KEY": VALID["ADMIN_ODDS_FEED_API_KEY"],
                "ADMIN_SETTLEMENT_API_KEY": VALID["ADMIN_SETTLEMENT_API_KEY"],
            },
        )
        self.assert_dependency_conditions(
            admin,
            {
                "postgres": "service_healthy",
                "kafka": "service_healthy",
                "topic-init": "service_completed_successfully",
                "wallet": "service_healthy",
                "risk": "service_healthy",
                "odds": "service_healthy",
                "settlement": "service_healthy",
            },
        )
        self.assertIn(
            "/actuator/health/readiness", admin["healthcheck"]["test"][1]
        )
        self.assertNotIn("/actuator/health >/dev/null", admin["healthcheck"]["test"][1])


if __name__ == "__main__":
    import unittest

    unittest.main()
