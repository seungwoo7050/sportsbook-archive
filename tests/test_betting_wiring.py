from tests.compose_contract_fixture import ComposeContractFixture
from tests.secret_fixture import VALID


class BettingWiringTest(ComposeContractFixture):
    def test_wires_auth_dependencies_and_odds_projection_topics(self) -> None:
        rendered = self.rendered()
        betting = rendered["services"]["betting"]
        environment = betting["environment"]

        self.assert_runtime_build(betting, "betting.jar")
        self.assertEqual(environment["BETTING_DB_URL"], "jdbc:postgresql://postgres:5432/betting")
        self.assertEqual(environment["BETTING_KAFKA_BOOTSTRAP"], "kafka:9092")
        self.assertEqual(environment["BETTING_REDIS_HOST"], "redis-odds")
        self.assertEqual(
            environment["BETTING_REDIS_HOST"],
            rendered["services"]["odds"]["environment"]["REDIS_HOST"],
        )
        self.assertEqual(environment["RISK_BASE_URL"], "http://risk:8083")
        self.assertEqual(environment["WALLET_BASE_URL"], "http://wallet:8081")
        self.assertEqual(
            {
                name: value
                for name, value in environment.items()
                if name.endswith("API_KEY")
            },
            {
                "BETTING_GATEWAY_API_KEY": VALID["GATEWAY_BETTING_API_KEY"],
                "BETTING_RISK_API_KEY": VALID["BETTING_RISK_API_KEY"],
                "BETTING_WALLET_API_KEY": VALID["BETTING_WALLET_API_KEY"],
            },
        )
        self.assert_dependency_conditions(
            betting,
            {
                "postgres": "service_healthy",
                "kafka": "service_healthy",
                "redis-odds": "service_healthy",
                "topic-init": "service_completed_successfully",
                "wallet": "service_healthy",
                "risk": "service_healthy",
                "odds": "service_healthy",
            },
        )
        self.assertIn("/actuator/health >/dev/null", betting["healthcheck"]["test"][1])


if __name__ == "__main__":
    import unittest

    unittest.main()
