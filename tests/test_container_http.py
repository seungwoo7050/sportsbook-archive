import subprocess
import unittest

from scripts.cold_gate.container_http import ContainerHttpClient


class FakeCompose:
    def __init__(self, failure: bool = False) -> None:
        self.calls = []
        self.failure = failure

    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        if self.failure:
            raise subprocess.CalledProcessError(7, arguments, stderr="secret")
        return subprocess.CompletedProcess(
            arguments,
            0,
            stdout="HTTP/1.1 202 Accepted\nX-Action: one\n\n{}\n__E2E_STATUS__:202\n",
        )


class ContainerHttpTest(unittest.TestCase):
    def test_uses_the_selected_container_loopback_only(self) -> None:
        compose = FakeCompose()
        response = ContainerHttpClient(compose, "admin").request(
            "POST",
            "/admin/v1/action",
            headers={"Authorization": "Bearer fixture"},
            body={"reason": "e2e"},
        )

        arguments, options = compose.calls[0]
        self.assertEqual(arguments[:4], ("exec", "-T", "admin", "curl"))
        self.assertEqual(arguments[-1], "http://localhost:8090/admin/v1/action")
        self.assertIn("Authorization: Bearer fixture", arguments)
        self.assertIn('{"reason":"e2e"}', arguments)
        self.assertEqual(options, {"capture_output": True})
        self.assertEqual(response.status, 202)
        self.assertEqual(response.header("X-Action"), "one")

    def test_rejects_services_paths_headers_and_timeouts_outside_contract(self) -> None:
        with self.assertRaisesRegex(ValueError, "not an application"):
            ContainerHttpClient(FakeCompose(), "postgres")
        client = ContainerHttpClient(FakeCompose(), "wallet")
        for options in (
            {"method": "get", "path": "/health"},
            {"method": "GET", "path": "//attacker.invalid"},
            {"method": "GET", "path": "/health", "timeout": 0},
            {"method": "GET", "path": "/health", "headers": {"X-Test": "bad\nvalue"}},
        ):
            with self.subTest(options=options):
                with self.assertRaises(ValueError):
                    client.request(**options)

    def test_does_not_relay_transport_output_in_errors(self) -> None:
        client = ContainerHttpClient(FakeCompose(failure=True), "admin")

        with self.assertRaisesRegex(RuntimeError, "admin") as captured:
            client.request("GET", "/actuator/health")

        self.assertNotIn("secret", str(captured.exception))


if __name__ == "__main__":
    unittest.main()
