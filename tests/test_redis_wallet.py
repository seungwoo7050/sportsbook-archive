from tests.redis_fixture import RedisFixture


class WalletRedisTest(RedisFixture):
    def test_isolates_durable_wallet_storage_from_other_domains(self) -> None:
        self.assert_redis_contract("redis-wallet", "redis-wallet-data")
        self.assert_isolated_values("redis-risk", "redis-odds", "redis-wallet")


if __name__ == "__main__":
    import unittest

    unittest.main()
