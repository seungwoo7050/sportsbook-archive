import unittest
from unittest import mock

from e2e import scenario_08_admin_candidates as subject


class FakeCorrections:
    def __init__(self, row: dict[str, str]) -> None:
        self.row = row

    def candidates(self, _event_id: str) -> list[dict[str, str]]:
        return [self.row]


class FakeDatabase:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str]] = []

    def one(self, service: str, statement: str) -> dict[str, str]:
        self.calls.append((service, statement))
        return {"eligible": "1"}


class AdminCandidateScenarioTest(unittest.TestCase):
    def test_requires_the_locked_future_hold_reason(self) -> None:
        row = {
            "candidate_id": "candidate",
            "state": "PENDING",
            "decision_reason": "FUTURE_HELD",
            "decided": "0",
        }
        runtime = type("Runtime", (), {"corrections": FakeCorrections(row)})()

        self.assertIs(subject._pending_candidate(runtime, "event"), row)

        row["decision_reason"] = ""
        with self.assertRaisesRegex(RuntimeError, "pending result candidate"):
            subject._pending_candidate(runtime, "event")

    def test_preserves_a_loaded_gate_eligibility_margin(self) -> None:
        self.assertGreaterEqual(subject.APPROVAL_HORIZON_MILLIS, 60_000)
        self.assertGreaterEqual(
            subject.APPROVAL_ELIGIBILITY_TIMEOUT_SECONDS * 1_000,
            subject.APPROVAL_HORIZON_MILLIS + 15_000,
        )

    def test_uses_the_settlement_database_eligibility_clock(self) -> None:
        database = FakeDatabase()
        runtime = type("Runtime", (), {"database": database})()
        candidate = "77000000-0000-7000-8000-000000000081"

        with mock.patch.object(subject.time, "time", side_effect=AssertionError):
            subject._wait_until_eligible(runtime, candidate)

        self.assertEqual(database.calls[0][0], "settlement")
        self.assertIn("settled_at <= CURRENT_TIMESTAMP", database.calls[0][1])
        self.assertIn(candidate, database.calls[0][1])


if __name__ == "__main__":
    unittest.main()
