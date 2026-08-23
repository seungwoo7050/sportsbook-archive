import unittest

from e2e.model import ScenarioIds


class E2eModelTest(unittest.TestCase):
    def test_allocates_disjoint_canonical_uuidv7_boundaries(self) -> None:
        first = ScenarioIds.create(1)
        second = ScenarioIds.create(2)

        self.assertEqual(first.user, "01000000-0000-7000-8000-000000000001")
        self.assertEqual(first.event, "11000000-0000-7000-8000-000000000001")
        self.assertEqual(first.market, "22000000-0000-7000-8000-000000000001")
        self.assertEqual(first.selection, "33000000-0000-7000-8000-000000000001")
        self.assertTrue({first.user, first.event, first.market, first.selection}.isdisjoint(
            {second.user, second.event, second.market, second.selection}
        ))

    def test_builds_the_fixed_wallet_and_single_bet_contracts(self) -> None:
        fixture = ScenarioIds.create(7)

        self.assertEqual(fixture.account(), {"userId": fixture.user, "currency": "KRW"})
        self.assertEqual(
            fixture.transfer(100_000),
            {"userId": fixture.user, "amount": {"amount": 100_000, "currency": "KRW"}},
        )
        placement = fixture.placement()
        self.assertEqual(placement["slipType"], {"type": "SINGLE"})
        self.assertEqual(placement["stake"], {"amount": 10_000, "currency": "KRW"})
        self.assertEqual(placement["selections"][0]["odds"], 2.0)

    def test_builds_exact_result_and_lifecycle_fixtures(self) -> None:
        fixture = ScenarioIds.create(9)
        result = fixture.match_result("WON", 1_700_000_000_000)
        lifecycle = fixture.cancelled(1_700_000_000_000)

        self.assertEqual(result["resultDetail"], {fixture.selection: "WON"})
        self.assertEqual(result["finalStatus"], "COMPLETED")
        self.assertEqual(lifecycle["status"], "CANCELLED")
        self.assertEqual(lifecycle["scheduledStartAt"], 1_699_999_940_000)

    def test_rejects_out_of_range_scenarios_and_invalid_events(self) -> None:
        with self.assertRaises(ValueError):
            ScenarioIds.create(0)
        fixture = ScenarioIds.create(1)
        with self.assertRaises(ValueError):
            fixture.transfer(0)
        with self.assertRaises(ValueError):
            fixture.match_result("UNKNOWN", 1)
        with self.assertRaises(ValueError):
            fixture.cancelled(60_000)


if __name__ == "__main__":
    unittest.main()
