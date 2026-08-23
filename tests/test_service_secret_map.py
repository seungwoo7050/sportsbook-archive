import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
MAP = ROOT / "config/service-secrets.map"
EXPECTED = {
    "GATEWAY_BETTING_API_KEY": ("gateway", "GATEWAY_BETTING_API_KEY", "betting", "BETTING_GATEWAY_API_KEY"),
    "GATEWAY_WALLET_API_KEY": ("gateway", "GATEWAY_WALLET_API_KEY", "wallet", "WALLET_GATEWAY_API_KEY"),
    "BETTING_RISK_API_KEY": ("betting", "BETTING_RISK_API_KEY", "risk", "INTERNAL_BETTING_SERVICE_API_KEY"),
    "BETTING_WALLET_API_KEY": ("betting", "BETTING_WALLET_API_KEY", "wallet", "WALLET_BETTING_SERVICE_API_KEY"),
    "SETTLEMENT_WALLET_API_KEY": ("settlement", "SETTLEMENT_WALLET_API_KEY", "wallet", "WALLET_SETTLEMENT_SERVICE_API_KEY"),
    "ADMIN_WALLET_API_KEY": ("admin", "ADMIN_WALLET_API_KEY", "wallet", "WALLET_ADMIN_API_KEY"),
    "ADMIN_RISK_API_KEY": ("admin", "ADMIN_RISK_API_KEY", "risk", "INTERNAL_ADMIN_API_KEY"),
    "ADMIN_ODDS_FEED_API_KEY": ("admin", "ADMIN_ODDS_FEED_API_KEY", "odds", "ADMIN_API_INTERNAL_KEY"),
    "ADMIN_SETTLEMENT_API_KEY": ("admin", "ADMIN_SETTLEMENT_API_KEY", "settlement", "SETTLEMENT_ADMIN_API_KEY"),
    "WALLET_PLATFORM_API_KEY": ("e2e-runner", "WALLET_PLATFORM_API_KEY", "wallet", "WALLET_PLATFORM_API_KEY"),
    "INTERNAL_PLATFORM_API_KEY": ("e2e-runner", "INTERNAL_PLATFORM_API_KEY", "risk", "INTERNAL_PLATFORM_API_KEY"),
}


class ServiceSecretMapTest(unittest.TestCase):
    def test_maps_each_logical_secret_to_one_exact_direction(self) -> None:
        rows = [
            line.split("|")
            for line in MAP.read_text().splitlines()
            if line and not line.startswith("#")
        ]
        self.assertTrue(all(len(row) == 5 for row in rows))
        actual = {row[0]: tuple(row[1:]) for row in rows}
        self.assertEqual(actual, EXPECTED)
        self.assertEqual(
            set(actual),
            set((ROOT / "config/required-secrets.txt").read_text().splitlines()),
        )
        self.assertEqual(
            len({(row[3], row[4]) for row in rows}),
            len(rows),
            "callee environment variables must have one logical owner",
        )


if __name__ == "__main__":
    unittest.main()
