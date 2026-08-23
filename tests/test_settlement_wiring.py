from tests.compose_contract_fixture import ComposeContractFixture
from tests.secret_fixture import VALID


class SettlementWiringTest(ComposeContractFixture):
    def test_wires_canonical_spring_environment_and_authentication(self) -> None:
        settlement = self.service("settlement")
        environment = settlement["environment"]

        self.assert_runtime_build(settlement, "settlement.jar")
        self.assertEqual(
            environment["SPRING_DATASOURCE_URL"],
            "jdbc:postgresql://postgres:5432/settlement",
        )
        self.assertEqual(environment["SPRING_KAFKA_BOOTSTRAP_SERVERS"], "kafka:9092")
        self.assertEqual(
            environment["SETTLEMENT_WALLET_BASE_URL"], "http://wallet:8081"
        )
        self.assertEqual(environment["SETTLEMENT_WORKERS_ENABLED"], "true")
        for obsolete in ("SETTLEMENT_DB_URL", "SETTLEMENT_KAFKA_BOOTSTRAP", "WALLET_BASE_URL"):
            self.assertNotIn(obsolete, environment)
        self.assertEqual(
            {
                name: value
                for name, value in environment.items()
                if name.endswith("API_KEY")
            },
            {
                "SETTLEMENT_ADMIN_API_KEY": VALID["ADMIN_SETTLEMENT_API_KEY"],
                "SETTLEMENT_WALLET_API_KEY": VALID["SETTLEMENT_WALLET_API_KEY"],
            },
        )
        self.assertEqual(
            {
                name: value
                for name, value in environment.items()
                if name.startswith("SETTLEMENT_TOPIC_")
            },
            {
                "SETTLEMENT_TOPIC_BET_PLACED": "bet.placed.v1",
                "SETTLEMENT_TOPIC_MATCH_RESULT": "match.result",
                "SETTLEMENT_TOPIC_EVENT_LIFECYCLE": "event.lifecycle",
                "SETTLEMENT_TOPIC_BET_SETTLED": "bet.settled.v1",
                "SETTLEMENT_TOPIC_BET_VOIDED": "bet.voided.v1",
                "SETTLEMENT_TOPIC_BET_REVISED": "bet.resolution.revised.v1",
            },
        )
        self.assert_dependency_conditions(
            settlement,
            {
                "postgres": "service_healthy",
                "kafka": "service_healthy",
                "topic-init": "service_completed_successfully",
                "wallet": "service_healthy",
                "consumer-assignment": "service_completed_successfully",
            },
        )
        self.assertIn(
            "/actuator/health/readiness", settlement["healthcheck"]["test"][1]
        )


if __name__ == "__main__":
    import unittest

    unittest.main()
