import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.stack import ColdStack


SHA = "0123456789abcdef0123456789abcdef01234567"


class FakeCompose:
    def __init__(self, context: ColdGateContext, failure: bool = False) -> None:
        self.context = context
        self.failure = failure
        self.calls = []
        self.absence_checks = 0

    def require_absent(self):
        self.absence_checks += 1

    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        if self.failure:
            raise subprocess.CalledProcessError(1, arguments, stderr="secret")
        if arguments[0] == "port":
            output = "127.0.0.1:54321\n"
        elif arguments[0] == "logs":
            output = "bounded log\n"
        else:
            output = ""
        return subprocess.CompletedProcess(arguments, 0, stdout=output)


class ColdStackTest(unittest.TestCase):
    def context(self, root: pathlib.Path) -> ColdGateContext:
        return ColdGateContext.create(root, SHA, "00000001")

    def test_validates_then_starts_the_full_stack_once(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context = self.context(pathlib.Path(temporary).resolve())
            compose = FakeCompose(context)
            environment = {"COMPOSE_PROJECT_NAME": context.project}

            ColdStack(context, compose).start(environment)

            self.assertEqual(compose.absence_checks, 1)
            self.assertEqual(compose.calls[0][0], ("config", "--quiet"))
            self.assertEqual(
                compose.calls[1][0],
                ("up", "--detach", "--build", "--wait", "--wait-timeout", "900"),
            )
            self.assertIs(compose.calls[1][1]["environment"], environment)

    def test_discovers_only_dynamic_loopback_ports_and_bounded_logs(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context = self.context(pathlib.Path(temporary).resolve())
            compose = FakeCompose(context)
            stack = ColdStack(context, compose)

            self.assertEqual(stack.loopback_port("gateway", 8080), 54321)
            self.assertEqual(stack.logs("wallet"), "bounded log\n")
            with self.assertRaises(ValueError):
                stack.loopback_port("postgres", 5432)
            with self.assertRaises(ValueError):
                stack.logs("unknown")

    def test_rejects_wrong_ownership_and_hides_startup_output(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context = self.context(pathlib.Path(temporary).resolve())
            compose = FakeCompose(context, failure=True)
            with self.assertRaisesRegex(RuntimeError, "environment"):
                ColdStack(context, compose).start({"COMPOSE_PROJECT_NAME": "wrong"})
            with self.assertRaisesRegex(RuntimeError, "startup failed") as captured:
                ColdStack(context, compose).start({"COMPOSE_PROJECT_NAME": context.project})
            self.assertNotIn("secret", str(captured.exception))


if __name__ == "__main__":
    unittest.main()
