import unittest

from e2e import scenario_09_admin_revision_retry as subject


REVISION = "55000000-0000-7000-8000-000000000009"


class FakeDatabase:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str]] = []

    def one(self, service: str, statement: str) -> dict[str, str]:
        self.calls.append((service, statement))
        return {"revision_id": REVISION}


class AdminRevisionScenarioTest(unittest.TestCase):
    def test_holds_the_queued_receipt_outside_the_scanner_window(self) -> None:
        self.assertGreaterEqual(subject.RETRY_HOLD_SECONDS, 120)

    def test_rearms_the_hold_before_settlement_restarts(self) -> None:
        database = FakeDatabase()
        runtime = type("Runtime", (), {"database": database})()

        subject._arm_retry_hold(runtime, REVISION)

        statement = database.calls[0][1]
        self.assertIn("wallet_next_attempt_at = CURRENT_TIMESTAMP", statement)
        self.assertIn("attempt_count = 12", statement)
        self.assertIn("next_retry_at IS NULL", statement)

    def test_releases_both_retry_clocks_atomically(self) -> None:
        database = FakeDatabase()
        runtime = type("Runtime", (), {"database": database})()

        subject._release_retry(runtime, REVISION)

        self.assertEqual(database.calls[0][0], "settlement")
        statement = database.calls[0][1]
        self.assertIn("wallet_next_attempt_at = CURRENT_TIMESTAMP", statement)
        self.assertIn("next_retry_at = CURRENT_TIMESTAMP", statement)
        self.assertIn("attempt_count = 0", statement)
        self.assertIn("wallet_next_attempt_at > CURRENT_TIMESTAMP", statement)
        self.assertIn("next_retry_at > CURRENT_TIMESTAMP", statement)


if __name__ == "__main__":
    unittest.main()
