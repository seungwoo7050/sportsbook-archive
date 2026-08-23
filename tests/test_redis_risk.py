from tests.redis_fixture import RedisFixture


class RiskRedisTest(RedisFixture):
    def test_uses_dedicated_aof_noeviction_storage(self) -> None:
        self.assert_redis_contract("redis-risk", "redis-risk-data")


if __name__ == "__main__":
    import unittest

    unittest.main()
