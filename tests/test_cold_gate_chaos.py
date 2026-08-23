import unittest

from scripts.cold_gate.chaos import ChaosClient, LOST_RESPONSE_TOXIC
from scripts.cold_gate.http import HttpResponse


class FakeHttp:
    def __init__(self, service: str = "toxiproxy") -> None:
        self.service = service
        self.calls = []

    def request(self, method, path, **options):
        self.calls.append((method, path, options))
        status = 204 if method == "DELETE" else 200
        body = b"" if status == 204 else b'{"enabled":true}'
        return HttpResponse(status, (), body)


class ColdGateChaosTest(unittest.TestCase):
    def test_controls_only_the_fixed_fault_boundaries(self) -> None:
        http = FakeHttp()
        client = ChaosClient(http)
        client.set_enabled("betting_to_risk", False)
        client.add_wallet_response_timeout()
        client.remove_wallet_response_timeout()

        self.assertEqual(http.calls[0][2]["body"], {"enabled": False})
        self.assertEqual(
            http.calls[1][2]["body"],
            {
                "name": LOST_RESPONSE_TOXIC,
                "type": "timeout",
                "stream": "downstream",
                "attributes": {"timeout": 0},
            },
        )
        self.assertTrue(http.calls[2][1].endswith("/toxics/" + LOST_RESPONSE_TOXIC))
        self.assertEqual(http.calls[2][0], "DELETE")

    def test_rejects_unscoped_proxy_names_and_foreign_containers(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "owned"):
            ChaosClient(FakeHttp("wallet"))
        client = ChaosClient(FakeHttp())
        with self.assertRaisesRegex(ValueError, "outside"):
            client.proxy("unrelated")


if __name__ == "__main__":
    unittest.main()
