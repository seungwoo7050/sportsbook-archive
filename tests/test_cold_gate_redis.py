import subprocess
import unittest

from scripts.cold_gate.redis import RedisClient


class FakeCompose:
    def __init__(self, output: str = "OK\n", failure: bool = False) -> None:
        self.output = output
        self.failure = failure
        self.calls = []

    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        if self.failure:
            raise subprocess.CalledProcessError(1, arguments, stderr="secret")
        return subprocess.CompletedProcess(arguments, 0, stdout=self.output)


class ColdGateRedisTest(unittest.TestCase):
    def test_executes_one_allowlisted_command_in_the_selected_instance(self) -> None:
        compose = FakeCompose()

        result = RedisClient(compose, "redis-odds").scalar(
            "set", "market:event:market", "OPEN", "EX", "3600"
        )

        arguments, options = compose.calls[0]
        self.assertEqual(
            arguments,
            (
                "exec", "-T", "redis-odds", "redis-cli", "--raw",
                "SET", "market:event:market", "OPEN", "EX", "3600",
            ),
        )
        self.assertEqual(options, {"capture_output": True})
        self.assertEqual(result, "OK")

    def test_rejects_unowned_instances_or_mutating_commands(self) -> None:
        with self.assertRaisesRegex(ValueError, "outside the release"):
            RedisClient(FakeCompose(), "redis")
        client = RedisClient(FakeCompose(), "redis-risk")
        for command in (("FLUSHALL",), ("GET", "bad\nkey"), ("SET", "", "value")):
            with self.subTest(command=command):
                with self.assertRaisesRegex(ValueError, "outside the gate"):
                    client.command(*command)

    def test_requires_a_scalar_and_hides_transport_output(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "one Redis"):
            RedisClient(FakeCompose("one\ntwo\n"), "redis-wallet").scalar("GET", "key")
        with self.assertRaisesRegex(RuntimeError, "redis-wallet") as captured:
            RedisClient(FakeCompose(failure=True), "redis-wallet").command("GET", "key")
        self.assertNotIn("secret", str(captured.exception))


if __name__ == "__main__":
    unittest.main()
