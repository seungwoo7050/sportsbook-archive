import base64
import json
import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.jwt import JwtSigner


SHA = "0123456789abcdef0123456789abcdef01234567"
USER = "01000000-0000-7000-8000-000000000001"


def decode(segment: str) -> dict[str, object]:
    padding = "=" * (-len(segment) % 4)
    return json.loads(base64.urlsafe_b64decode(segment + padding))


class ColdGateJwtTest(unittest.TestCase):
    def fixture(self, root: pathlib.Path) -> tuple[ColdGateContext, pathlib.Path]:
        context = ColdGateContext.create(root, SHA, "00000001")
        secret_dir = context.runtime / "secrets"
        secret_dir.mkdir(mode=0o700)
        private_key = secret_dir / "jwt-private.pem"
        private_key.write_text("fixture private key")
        private_key.chmod(0o600)
        return context, private_key

    def test_signs_exact_user_and_admin_claim_shapes(self) -> None:
        calls = []

        def runner(command, **options):
            calls.append((command, options))
            return subprocess.CompletedProcess(command, 0, stdout=b"signature")

        with tempfile.TemporaryDirectory() as temporary:
            context, key = self.fixture(pathlib.Path(temporary).resolve())
            signer = JwtSigner(context, key, runner)

            user = signer.user(USER, 1_700_000_000)
            admin = signer.admin(1_700_000_000)

        user_header, user_payload, user_signature = user.split(".")
        self.assertEqual(decode(user_header), {"alg": "RS256", "typ": "JWT"})
        self.assertEqual(
            decode(user_payload),
            {"exp": 1_700_001_200, "iat": 1_700_000_000, "roles": ["USER"], "sub": USER},
        )
        self.assertEqual(
            decode(admin.split(".")[1]),
            {
                "exp": 1_700_001_200,
                "iat": 1_700_000_000,
                "iss": "sportsbook-admin-e2e",
                "nbf": 1_699_999_995,
                "role": "ADMIN",
                "sub": "e2e-admin",
            },
        )
        self.assertEqual(user_signature, "c2lnbmF0dXJl")
        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[0][0][:4], ["openssl", "dgst", "-sha256", "-sign"])
        self.assertNotIn("fixture private key", str(calls))

    def test_rejects_non_uuidv7_subjects(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, key = self.fixture(pathlib.Path(temporary).resolve())
            signer = JwtSigner(context, key, lambda *args, **kwargs: None)

            with self.assertRaisesRegex(ValueError, "UUIDv7"):
                signer.user("00000000-0000-4000-8000-000000000001", 1)

    def test_rejects_broad_private_key_permissions(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, key = self.fixture(pathlib.Path(temporary).resolve())
            key.chmod(0o644)

            with self.assertRaisesRegex(RuntimeError, "permissions"):
                JwtSigner(context, key)


if __name__ == "__main__":
    unittest.main()
