import pathlib
import subprocess
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
ADMIN_SHA = "2fb55910475b31084e6489bf01c34cc970c96874"


class AdminHandoffTest(unittest.TestCase):
    def test_locks_and_consumes_the_final_wave_three_contract(self) -> None:
        admin_row = next(
            line
            for line in (ROOT / "services.lock").read_text().splitlines()
            if line.startswith("admin|")
        )
        self.assertEqual(
            admin_row,
            f"admin|admin-api|{ADMIN_SHA}|admin-api-1.0.0.jar",
        )
        readme = subprocess.check_output(
            ["git", "show", f"{ADMIN_SHA}:README.md"], cwd=ROOT, text=True
        )
        for contract in (
            "POST /admin/v1/wallet/{userId}/refund",
            "PATCH /admin/v1/risk/users/{userId}/limits",
            "POST /admin/v1/events/{eventId}/markets/{marketId}/suspend",
            "POST /admin/v1/settlements/result-candidates/{candidateId}/approve",
            "POST /admin/v1/settlements/revisions/{revisionId}/retry",
            "GET /admin/v1/audit-logs/{actionId}",
            "ADMIN_WALLET_API_KEY",
            "ADMIN_RISK_API_KEY",
            "ADMIN_ODDS_FEED_API_KEY",
            "ADMIN_SETTLEMENT_API_KEY",
        ):
            with self.subTest(contract=contract):
                self.assertIn(contract, readme)
        self.assertIn("There are no settlement void or replay endpoints.", readme)


if __name__ == "__main__":
    unittest.main()
