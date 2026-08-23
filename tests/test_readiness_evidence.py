import pathlib
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.http import HttpResponse
from scripts.cold_gate.readiness_evidence import READINESS_ENDPOINTS, ReadinessEvidence
from scripts.cold_gate.redaction import EvidenceRedactor


SHA = "0123456789abcdef0123456789abcdef01234567"
METRICS = (
    b'wallet_integrity_total_drift{service="wallet-service"} 0.0\n'
    b'wallet_integrity_scan_failed{service="wallet-service"} 0.0\n'
    b'wallet_integrity_last_checked_epoch_seconds{service="wallet-service"} 1.777e9\n'
)


class FakeClient:
    def __init__(self, service: str, statuses: dict[str, str], metrics: bytes) -> None:
        self.service = service
        self.statuses = statuses
        self.metrics = metrics
        self.calls = []

    def request(self, method: str, path: str) -> HttpResponse:
        self.calls.append((method, path))
        if path == "/actuator/prometheus":
            return HttpResponse(200, (), self.metrics)
        body = ('{"status":"%s"}' % self.statuses[self.service]).encode()
        return HttpResponse(200, (), body)


class ReadinessEvidenceTest(unittest.TestCase):
    def fixture(self, root: pathlib.Path, statuses=None, metrics=METRICS):
        context = ColdGateContext.create(root, SHA, "00000001")
        store = EvidenceStore(context, EvidenceRedactor(["redaction-secret-value"]))
        values = statuses or {service: "UP" for service, _path in READINESS_ENDPOINTS}
        clients = []

        def factory(_compose, service):
            client = FakeClient(service, values, metrics)
            clients.append(client)
            return client

        compose = type("FakeCompose", (), {"context": context})()
        return context, store, clients, factory, compose

    def test_records_exact_readiness_and_clean_first_scan(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, store, clients, factory, compose = self.fixture(pathlib.Path(temporary))

            ReadinessEvidence(compose, store, factory).capture()

            lines = (context.evidence / "readiness.tsv").read_text().splitlines()
            self.assertEqual(lines[0], "kind\tservice\tcheck\tvalue")
            self.assertEqual([line.split("\t")[1] for line in lines[1:8]],
                             [service for service, _path in READINESS_ENDPOINTS])
            self.assertEqual(lines[-3:], [
                "integrity\twallet\twallet_integrity_total_drift\t0",
                "integrity\twallet\twallet_integrity_scan_failed\t0",
                "integrity\twallet\twallet_integrity_last_checked_epoch_seconds\t1777000000",
            ])
            self.assertEqual(clients[-1].calls, [("GET", "/actuator/prometheus")])

    def test_rejects_non_up_service_or_integrity_drift_before_writing(self) -> None:
        cases = (
            ({"admin": "DOWN"}, METRICS, "admin readiness"),
            ({}, METRICS.replace(b'} 0.0', b'} 1.0', 1), "not clean"),
        )
        for overrides, metrics, message in cases:
            with self.subTest(message=message), tempfile.TemporaryDirectory() as temporary:
                statuses = {service: "UP" for service, _path in READINESS_ENDPOINTS}
                statuses.update(overrides)
                context, store, _clients, factory, compose = self.fixture(
                    pathlib.Path(temporary), statuses, metrics
                )
                with self.assertRaisesRegex(RuntimeError, message):
                    ReadinessEvidence(compose, store, factory).capture()
                self.assertEqual(list(context.evidence.iterdir()), [])

    def test_requires_the_exact_wallet_service_label(self) -> None:
        cases = (
            METRICS.replace(b'{service="wallet-service"}', b''),
            METRICS.replace(b'wallet-service', b'other-service'),
            METRICS.replace(b'} 0.0', b',region="test"} 0.0', 1),
            METRICS + METRICS.splitlines(keepends=True)[0],
        )
        for metrics in cases:
            with self.subTest(metrics=metrics):
                client = FakeClient("wallet", {}, metrics)
                with self.assertRaisesRegex(RuntimeError, "wallet-service-labelled"):
                    ReadinessEvidence._metrics(client)


if __name__ == "__main__":
    unittest.main()
