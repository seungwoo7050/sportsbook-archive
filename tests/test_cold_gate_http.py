import io
import json
import urllib.error
import unittest
from email.message import Message

from scripts.cold_gate.http import HostHttpClient, HttpResponse, parse_curl_response


class FakeResponse:
    def __init__(self, status: int, body: bytes, headers: Message) -> None:
        self.status = status
        self.body = body
        self.headers = headers

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self) -> bytes:
        return self.body


class ColdGateHttpTest(unittest.TestCase):
    def test_sends_canonical_json_to_an_exact_loopback_path(self) -> None:
        calls = []
        response_headers = Message()
        response_headers.add_header("Location", "/api/v1/bets/id")

        def opener(request, **options):
            calls.append((request, options))
            return FakeResponse(201, b'{"status":"ACCEPTED"}', response_headers)

        response = HostHttpClient("http://127.0.0.1:54321", opener).request(
            "POST",
            "/api/v1/bets",
            headers={"Authorization": "Bearer token"},
            body={"stake": 10000},
        )

        request, options = calls[0]
        self.assertEqual(request.full_url, "http://127.0.0.1:54321/api/v1/bets")
        self.assertEqual(request.method, "POST")
        self.assertEqual(json.loads(request.data), {"stake": 10000})
        self.assertEqual(request.headers["Content-type"], "application/json")
        self.assertEqual(options, {"timeout": 15.0})
        self.assertEqual(response.require_status(201).json(), {"status": "ACCEPTED"})
        self.assertEqual(response.header("location"), "/api/v1/bets/id")

    def test_preserves_an_http_error_as_an_assertable_response(self) -> None:
        headers = Message()
        headers.add_header("Content-Type", "application/problem+json")

        def opener(request, **_options):
            raise urllib.error.HTTPError(
                request.full_url, 409, "Conflict", headers, io.BytesIO(b'{"code":"CONFLICT"}')
            )

        response = HostHttpClient("http://127.0.0.1:8080", opener).request("GET", "/conflict")

        self.assertEqual(response.status, 409)
        self.assertEqual(response.json(), {"code": "CONFLICT"})

    def test_rejects_escaped_targets_and_ambiguous_headers(self) -> None:
        with self.assertRaisesRegex(ValueError, "loopback"):
            HostHttpClient("http://gateway:8080")
        client = HostHttpClient("http://127.0.0.1:8080", lambda *_args, **_kwargs: None)
        with self.assertRaisesRegex(ValueError, "absolute"):
            client.request("GET", "//attacker.invalid/path")
        response = HttpResponse(202, (("X-Id", "one"), ("x-id", "two")), b"")
        with self.assertRaisesRegex(RuntimeError, "exactly one"):
            response.header("X-Id")

    def test_parses_a_framed_container_receipt_without_losing_headers(self) -> None:
        response = parse_curl_response(
            b"HTTP/1.1 202 Accepted\r\n"
            b"X-Admin-Action-Id: first\r\n"
            b"X-Trace: trace\r\n\r\n"
            b'{"outcome":"QUEUED"}\n__E2E_STATUS__:202\n'
        )

        self.assertEqual(response.status, 202)
        self.assertEqual(response.header("X-Admin-Action-Id"), "first")
        self.assertEqual(response.json(), {"outcome": "QUEUED"})

    def test_rejects_unframed_or_malformed_container_receipts(self) -> None:
        invalid = (
            b"HTTP/1.1 200 OK\r\n\r\nbody",
            b"HTTP/1.1 200 OK\r\nBad\r\n\r\n\n__E2E_STATUS__:200\n",
            b"HTTP/1.1 200 OK\r\n\r\n\n__E2E_STATUS__:999\n",
        )
        for receipt in invalid:
            with self.subTest(receipt=receipt):
                with self.assertRaises(RuntimeError):
                    parse_curl_response(receipt)


if __name__ == "__main__":
    unittest.main()
