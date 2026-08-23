import unittest

from e2e import scenario_08_admin_candidates as subject


class FakeCorrections:
    def __init__(self, row: dict[str, str]) -> None:
        self.row = row

    def candidates(self, _event_id: str) -> list[dict[str, str]]:
        return [self.row]


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


if __name__ == "__main__":
    unittest.main()
