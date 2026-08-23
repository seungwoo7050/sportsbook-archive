from tests.compose_contract_fixture import ComposeContractFixture
from tests.secret_fixture import VALID


class RiskWiringTest(ComposeContractFixture):
    def test_wires_authentication_redis_kafka_and_readiness(self) -> None:
        risk = self.service("risk")
        environment = risk["environment"]

        self.assert_runtime_build(risk, "risk.jar")
        self.assertEqual(environment["REDIS_HOST"], "redis-risk")
        self.assertEqual(environment["KAFKA_BOOTSTRAP"], "kafka:9092")
        self.assertEqual(
            {
                name: value
                for name, value in environment.items()
                if name.endswith("API_KEY")
            },
            {
                "INTERNAL_BETTING_SERVICE_API_KEY": VALID["BETTING_RISK_API_KEY"],
                "INTERNAL_ADMIN_API_KEY": VALID["ADMIN_RISK_API_KEY"],
                "INTERNAL_PLATFORM_API_KEY": VALID["INTERNAL_PLATFORM_API_KEY"],
            },
        )
        self.assertEqual(
            risk["healthcheck"]["test"],
            [
                "CMD-SHELL",
                "curl --fail --silent --show-error "
                "http://localhost:8083/actuator/health/readiness >/dev/null",
            ],
        )
        self.assert_dependency_conditions(
            risk,
            {
                "kafka": "service_healthy",
                "redis-risk": "service_healthy",
                "topic-init": "service_completed_successfully",
                "secret-preflight": "service_completed_successfully",
            },
        )


if __name__ == "__main__":
    import unittest

    unittest.main()
