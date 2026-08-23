import json
import unittest
from email.message import Message

from scripts.cold_gate.chaos import ChaosClient, LOST_RESPONSE_TOXIC


class FakeResponse:
    def __init__(self, status: int, body: bytes) -> None:
        self.status = status
        self.body = body
        self.headers = Message()

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self) -> bytes:
        return self.body


class ColdGateChaosTest(unittest.TestCase):
    def test_controls_only_the_fixed_fault_boundaries(self) -> None:
        requests = []

        def opener(request, **_options):
            requests.append(request)
            status = 204 if request.method == "DELETE" else 200
            return FakeResponse(status, b"" if status == 204 else b'{"enabled":true}')

        client = ChaosClient(54321, opener)
        client.set_enabled("betting_to_risk", False)
        client.add_wallet_response_timeout()
        client.remove_wallet_response_timeout()

        self.assertEqual(json.loads(requests[0].data), {"enabled": False})
        self.assertEqual(
            json.loads(requests[1].data),
            {
                "name": LOST_RESPONSE_TOXIC,
                "type": "timeout",
                "stream": "downstream",
                "attributes": {"timeout": 0},
            },
        )
        self.assertTrue(requests[2].full_url.endswith("/toxics/" + LOST_RESPONSE_TOXIC))
        self.assertEqual(requests[2].method, "DELETE")

    def test_rejects_unscoped_proxy_names_and_non_loopback_publication(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "loopback"):
            ChaosClient(0)
        client = ChaosClient(54321, lambda *_args, **_options: None)
        with self.assertRaisesRegex(ValueError, "outside"):
            client.proxy("unrelated")


if __name__ == "__main__":
    unittest.main()
