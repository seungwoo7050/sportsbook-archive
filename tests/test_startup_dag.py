from tests.compose_contract_fixture import ComposeContractFixture


RANK = {
    "postgres": 0,
    "kafka": 0,
    "redis-risk": 0,
    "redis-odds": 0,
    "redis-wallet": 0,
    "redis-gateway": 0,
    "secret-preflight": 0,
    "topic-init": 1,
    "wallet": 2,
    "risk": 2,
    "odds": 2,
    "betting": 3,
    "gateway": 4,
    "consumer-assignment": 5,
    "settlement": 6,
    "admin": 7,
}


class StartupDagTest(ComposeContractFixture):
    def test_orders_infrastructure_topics_services_and_control_plane(self) -> None:
        services = self.rendered()["services"]

        for service, rank in RANK.items():
            for dependency in services[service].get("depends_on", {}):
                with self.subTest(service=service, dependency=dependency):
                    self.assertIn(dependency, RANK)
                    self.assertLess(RANK[dependency], rank)

        self.assert_dependency_conditions(
            services["topic-init"],
            {
                "postgres": "service_healthy",
                "kafka": "service_healthy",
                "redis-risk": "service_healthy",
                "redis-odds": "service_healthy",
                "redis-wallet": "service_healthy",
                "redis-gateway": "service_healthy",
            },
        )
        self.assert_dependency_conditions(
            services["consumer-assignment"],
            {
                "kafka": "service_healthy",
                "topic-init": "service_completed_successfully",
                "betting": "service_healthy",
                "gateway": "service_healthy",
            },
        )
        self.assertEqual(
            services["settlement"]["depends_on"]["consumer-assignment"]["condition"],
            "service_completed_successfully",
        )
        self.assertEqual(
            services["admin"]["depends_on"]["settlement"]["condition"],
            "service_healthy",
        )


if __name__ == "__main__":
    import unittest

    unittest.main()
