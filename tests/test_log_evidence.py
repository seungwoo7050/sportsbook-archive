import pathlib
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.inventory import SERVICES
from scripts.cold_gate.log_evidence import LogEvidence
from scripts.cold_gate.redaction import EvidenceRedactor


SHA = "0" * 40
SECRET = "service-secret-value-000000000000"


class FakeStack:
    def __init__(self, context):
        self.context = context
        self.calls = []

    def logs(self, service):
        self.calls.append(service)
        return f"{service} key={SECRET}\n"


class LogEvidenceTest(unittest.TestCase):
    def test_captures_exact_bounded_redacted_log_inventory(self):
        with tempfile.TemporaryDirectory() as temporary:
            context = ColdGateContext.create(
                pathlib.Path(temporary).resolve(), SHA, "00000001"
            )
            store = EvidenceStore(context, EvidenceRedactor((SECRET,)))
            stack = FakeStack(context)

            LogEvidence(stack, store).capture()

            self.assertEqual(stack.calls, list(SERVICES))
            logs = sorted(path.name for path in (context.evidence / "logs").iterdir())
            self.assertEqual(logs, sorted(f"{service}.log" for service in SERVICES))
            self.assertNotIn(SECRET, (context.evidence / "logs/wallet.log").read_text())

    def test_rejects_foreign_ownership_and_unsafe_log_bytes(self):
        with tempfile.TemporaryDirectory() as left, tempfile.TemporaryDirectory() as right:
            context = ColdGateContext.create(pathlib.Path(left), SHA, "00000001")
            foreign = ColdGateContext.create(pathlib.Path(right), SHA, "00000002")
            store = EvidenceStore(context, EvidenceRedactor((SECRET,)))
            with self.assertRaisesRegex(RuntimeError, "ownership"):
                LogEvidence(FakeStack(foreign), store)

            stack = FakeStack(context)
            stack.logs = lambda _service: "unsafe\0log"
            with self.assertRaisesRegex(RuntimeError, "unsafe"):
                LogEvidence(stack, store).capture()
            self.assertFalse((context.evidence / "logs").exists())


if __name__ == "__main__":
    unittest.main()
