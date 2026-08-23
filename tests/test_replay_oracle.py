import unittest

from e2e.replay_oracle import ReplayOracle


BET = "66000000-0000-7000-8000-000000000011"


class FakeDatabase:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str]] = []

    def one(self, service: str, statement: str) -> dict[str, str]:
        self.calls.append((service, statement))
        return {"receipt_count": "1", "processed": "1"}


class ReplayOracleTest(unittest.TestCase):
    def test_reads_one_processed_wallet_receipt_for_the_bet(self) -> None:
        database = FakeDatabase()

        receipt = ReplayOracle(database, object()).wallet_receipt(BET)

        self.assertEqual(receipt, {"receipt_count": "1", "processed": "1"})
        self.assertEqual(database.calls[0][0], "betting")
        statement = database.calls[0][1]
        self.assertIn("FROM wallet_event_receipt", statement)
        self.assertIn("processed_at IS NOT NULL", statement)
        self.assertIn(BET, statement)


if __name__ == "__main__":
    unittest.main()
