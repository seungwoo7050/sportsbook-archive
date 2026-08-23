from tests.compose_contract_fixture import ComposeContractFixture
from tests.secret_fixture import VALID


class WalletWiringTest(ComposeContractFixture):
    def test_wires_canonical_environment_outbox_and_health(self) -> None:
        wallet = self.service("wallet")
        environment = wallet["environment"]

        self.assert_runtime_build(wallet, "wallet.jar")
        self.assertEqual(environment["WALLET_DB_URL"], "jdbc:postgresql://postgres:5432/wallet")
        self.assertEqual(environment["WALLET_REDIS_HOST"], "redis-wallet")
        self.assertEqual(environment["WALLET_KAFKA_BOOTSTRAP"], "kafka:9092")
        self.assertEqual(environment["WALLET_OUTBOX_ENABLED"], "true")
        self.assertEqual(environment["WALLET_INTEGRITY_ENABLED"], "true")
        self.assertEqual(environment["WALLET_RECOVERY_ENABLED"], "true")
        self.assertEqual(
            {
                name: value
                for name, value in environment.items()
                if name.endswith("API_KEY")
            },
            {
                "WALLET_PLATFORM_API_KEY": VALID["WALLET_PLATFORM_API_KEY"],
                "WALLET_GATEWAY_API_KEY": VALID["GATEWAY_WALLET_API_KEY"],
                "WALLET_BETTING_SERVICE_API_KEY": VALID["BETTING_WALLET_API_KEY"],
                "WALLET_SETTLEMENT_SERVICE_API_KEY": VALID["SETTLEMENT_WALLET_API_KEY"],
                "WALLET_ADMIN_API_KEY": VALID["ADMIN_WALLET_API_KEY"],
            },
        )
        self.assertEqual(
            wallet["healthcheck"]["test"],
            [
                "CMD-SHELL",
                "curl --fail --silent --show-error "
                "http://localhost:8081/actuator/health >/dev/null",
            ],
        )
        self.assert_dependency_conditions(
            wallet,
            {
                "postgres": "service_healthy",
                "kafka": "service_healthy",
                "redis-wallet": "service_healthy",
                "topic-init": "service_completed_successfully",
                "secret-preflight": "service_completed_successfully",
            },
        )


if __name__ == "__main__":
    import unittest

    unittest.main()
