from tests.compose_contract_fixture import ComposeContractFixture, PUBLIC_KEY
from tests.secret_fixture import VALID


class GatewayWiringTest(ComposeContractFixture):
    def test_wires_inline_pem_single_replica_and_exact_dependencies(self) -> None:
        gateway = self.service("gateway")
        environment = gateway["environment"]

        self.assert_runtime_build(gateway, "gateway.jar")
        self.assertEqual(environment["GATEWAY_REDIS_HOST"], "redis-gateway")
        self.assertEqual(environment["GATEWAY_KAFKA_BOOTSTRAP"], "kafka:9092")
        self.assertEqual(environment["GATEWAY_BETTING_URI"], "http://betting:8082")
        self.assertEqual(environment["GATEWAY_WALLET_URI"], "http://wallet:8081")
        self.assertEqual(environment["GATEWAY_ODDS_FEED_URI"], "http://odds:8085")
        self.assertEqual(environment["GATEWAY_JWT_PUBLIC_KEY"], PUBLIC_KEY)
        self.assertIn("\n", environment["GATEWAY_JWT_PUBLIC_KEY"])
        self.assertEqual(
            {
                name: value
                for name, value in environment.items()
                if name.endswith("API_KEY")
            },
            {
                "GATEWAY_BETTING_API_KEY": VALID["GATEWAY_BETTING_API_KEY"],
                "GATEWAY_WALLET_API_KEY": VALID["GATEWAY_WALLET_API_KEY"],
            },
        )
        self.assertEqual(gateway["deploy"]["replicas"], 1)
        self.assertIn(
            "/actuator/health/readiness", gateway["healthcheck"]["test"][1]
        )
        self.assert_dependency_conditions(
            gateway,
            {
                "kafka": "service_healthy",
                "redis-gateway": "service_healthy",
                "topic-init": "service_completed_successfully",
                "wallet": "service_healthy",
                "odds": "service_healthy",
                "betting": "service_healthy",
            },
        )
        self.assertEqual(
            {
                name: value
                for name, value in environment.items()
                if name.startswith("GATEWAY_TOPIC_")
            },
            {
                "GATEWAY_TOPIC_ODDS_CHANGED": "odds.changed",
                "GATEWAY_TOPIC_BET_SETTLED": "bet.settled.v1",
                "GATEWAY_TOPIC_BET_VOIDED": "bet.voided.v1",
                "GATEWAY_TOPIC_BET_RESOLUTION_REVISED": "bet.resolution.revised.v1",
            },
        )


if __name__ == "__main__":
    import unittest

    unittest.main()
