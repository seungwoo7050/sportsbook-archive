import pathlib
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.redaction import EvidenceRedactor
from scripts.cold_gate.scenario_evidence import EXPECTED_SCENARIOS, ScenarioEvidence


SHA = "0123456789abcdef0123456789abcdef01234567"


class ScenarioEvidenceTest(unittest.TestCase):
    def store(self, root: pathlib.Path) -> EvidenceStore:
        context = ColdGateContext.create(root, SHA, "00000001")
        return EvidenceStore(context, EvidenceRedactor(["redaction-secret-value"]))

    def test_records_the_fixed_thirteen_passes_in_order(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            store = self.store(pathlib.Path(temporary))

            ScenarioEvidence(store).capture(EXPECTED_SCENARIOS)

            lines = (store.context.evidence / "scenarios.tsv").read_text().splitlines()
            self.assertEqual(lines[0], "scenario\tresult")
            self.assertEqual(len(lines), 14)
            self.assertEqual(
                lines[1:], [f"{name}\tPASS" for name in EXPECTED_SCENARIOS]
            )

    def test_rejects_missing_duplicate_or_out_of_order_passes(self) -> None:
        invalid = (
            EXPECTED_SCENARIOS[:-1],
            EXPECTED_SCENARIOS[:-1] + (EXPECTED_SCENARIOS[0],),
            tuple(reversed(EXPECTED_SCENARIOS)),
        )
        for passed in invalid:
            with self.subTest(passed=passed), tempfile.TemporaryDirectory() as temporary:
                store = self.store(pathlib.Path(temporary))
                with self.assertRaisesRegex(RuntimeError, "incomplete or out of order"):
                    ScenarioEvidence(store).capture(passed)
                self.assertEqual(list(store.context.evidence.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
