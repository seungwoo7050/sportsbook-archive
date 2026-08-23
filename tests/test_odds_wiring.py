from tests.compose_contract_fixture import ComposeContractFixture
from tests.secret_fixture import VALID


class OddsWiringTest(ComposeContractFixture):
    def test_wires_admin_auth_projection_store_and_critical_readiness(self) -> None:
        odds = self.service("odds")
        environment = odds["environment"]

        self.assert_runtime_build(odds, "odds.jar")
        self.assertEqual(environment["SPRING_PROFILES_ACTIVE"], "mock")
        self.assertEqual(environment["REDIS_HOST"], "redis-odds")
        self.assertEqual(environment["KAFKA_BOOTSTRAP_SERVERS"], "kafka:9092")
        self.assertEqual(
            environment["ADMIN_API_INTERNAL_KEY"], VALID["ADMIN_ODDS_FEED_API_KEY"]
        )
        self.assertEqual(environment["ODDSFEED_MOCK_SCENARIOS_AUTO_ROTATE"], "false")
        self.assertEqual(
            odds["healthcheck"]["test"],
            [
                "CMD-SHELL",
                "curl --fail --silent --show-error "
                "http://localhost:8085/actuator/health/readiness >/dev/null",
            ],
        )
        self.assert_dependency_conditions(
            odds,
            {
                "kafka": "service_healthy",
                "redis-odds": "service_healthy",
                "topic-init": "service_completed_successfully",
            },
        )


if __name__ == "__main__":
    import unittest

    unittest.main()
