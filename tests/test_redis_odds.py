from tests.redis_fixture import RedisFixture


class OddsRedisTest(RedisFixture):
    def test_isolates_durable_projection_storage_from_risk(self) -> None:
        self.assert_redis_contract("redis-odds", "redis-odds-data")
        self.assert_isolated_values("redis-risk", "redis-odds")


if __name__ == "__main__":
    import unittest

    unittest.main()
