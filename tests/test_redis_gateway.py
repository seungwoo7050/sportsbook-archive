import json

from tests.redis_fixture import RedisFixture


REDIS_SERVICES = {"redis-risk", "redis-odds", "redis-wallet", "redis-gateway"}


class GatewayRedisTest(RedisFixture):
    def test_completes_the_exact_four_instance_isolation_boundary(self) -> None:
        self.assert_redis_contract("redis-gateway", "redis-gateway-data")
        self.assert_isolated_values(*sorted(REDIS_SERVICES))

        rendered = self.compose("config", "--format", "json")
        self.assertEqual(rendered.returncode, 0, rendered.stderr)
        services = json.loads(rendered.stdout)["services"]
        self.assertEqual(
            {name for name in services if name.startswith("redis-")}, REDIS_SERVICES
        )
        volumes = {
            services[name]["volumes"][0]["source"] for name in REDIS_SERVICES
        }
        self.assertEqual(
            volumes,
            {
                "redis-risk-data",
                "redis-odds-data",
                "redis-wallet-data",
                "redis-gateway-data",
            },
        )


if __name__ == "__main__":
    import unittest

    unittest.main()
