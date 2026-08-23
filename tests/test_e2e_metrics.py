import decimal
import unittest

from e2e.metrics import metric_value
from scripts.cold_gate.http import HttpResponse


NAME = "betting_resolution_revision_gaps_total"
LABEL = '{service="betting-service"}'


class FakeClient:
    def __init__(self, body: bytes) -> None:
        self.body = body

    def request(self, method: str, path: str) -> HttpResponse:
        if (method, path) != ("GET", "/actuator/prometheus"):
            raise AssertionError("unexpected metrics request")
        return HttpResponse(200, (), self.body)


class E2eMetricsTest(unittest.TestCase):
    def test_reads_one_exact_betting_service_sample(self) -> None:
        client = FakeClient(f"{NAME}{LABEL} 1.25e2\n".encode())

        self.assertEqual(metric_value(client, NAME), decimal.Decimal("125"))

    def test_rejects_missing_wrong_extra_or_duplicate_labels(self) -> None:
        valid = f"{NAME}{LABEL} 1\n"
        cases = (
            f"{NAME} 1\n",
            f'{NAME}{{service="other-service"}} 1\n',
            f'{NAME}{{service="betting-service",region="test"}} 1\n',
            valid + valid,
        )
        for body in cases:
            with self.subTest(body=body):
                with self.assertRaisesRegex(RuntimeError, "betting-service-labelled"):
                    metric_value(FakeClient(body.encode()), NAME)


if __name__ == "__main__":
    unittest.main()
