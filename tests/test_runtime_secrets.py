import pathlib
import stat
import subprocess
import sys
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.secrets import RuntimeSecrets


ROOT = pathlib.Path(__file__).resolve().parents[1]
SHA = "0123456789abcdef0123456789abcdef01234567"


class RuntimeSecretsTest(unittest.TestCase):
    def test_generates_distinct_keys_and_inline_public_pem(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            (root / "config").mkdir()
            (root / "config/required-secrets.txt").write_text(
                (ROOT / "config/required-secrets.txt").read_text()
            )
            context = ColdGateContext.create(root, SHA, "00000001")

            generated = RuntimeSecrets.generate(context)

            names = (ROOT / "config/required-secrets.txt").read_text().splitlines()
            service_values = [generated.environment[name] for name in names]
            self.assertEqual(len(service_values), 11)
            self.assertEqual(len(set(service_values)), 11)
            self.assertTrue(all(len(value) >= 32 for value in service_values))
            public_key = generated.environment["GATEWAY_JWT_PUBLIC_KEY"]
            self.assertEqual(public_key, generated.environment["ADMIN_JWT_PUBLIC_KEY"])
            self.assertTrue(public_key.startswith("-----BEGIN PUBLIC KEY-----"))
            self.assertEqual(generated.environment["COMPOSE_PROJECT_NAME"], context.project)
            gateway_port = generated.gateway_port
            self.assertTrue(0 < gateway_port <= 65535)
            self.assertEqual(
                stat.S_IMODE(generated.private_key.stat().st_mode), 0o600
            )
            self.assertFalse(any(context.runtime.rglob("*.env")))

            checked = subprocess.run(
                [sys.executable, str(ROOT / "scripts/check-secrets.py")],
                cwd=ROOT,
                env=generated.environment,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(checked.returncode, 0, checked.stderr)
            for value in generated.secret_values:
                self.assertNotIn(value, checked.stdout + checked.stderr)

    def test_rejects_an_invalid_gateway_port(self) -> None:
        generated = RuntimeSecrets({"GATEWAY_HOST_PORT": "0"}, pathlib.Path("key"), ())

        with self.assertRaisesRegex(RuntimeError, "gateway port"):
            _ = generated.gateway_port


if __name__ == "__main__":
    unittest.main()
