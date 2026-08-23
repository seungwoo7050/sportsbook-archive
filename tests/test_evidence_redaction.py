import unittest

from scripts.cold_gate.redaction import EvidenceRedactor


class EvidenceRedactionTest(unittest.TestCase):
    def test_redacts_exact_secrets_jwts_and_pem_blocks(self) -> None:
        secrets = (
            "wallet-secret-value-0000000000000001",
            "database-password-value",
        )
        redactor = EvidenceRedactor(secrets)
        source = """
        X-API-Key: wallet-secret-value-0000000000000001
        password=database-password-value
        Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.signature
        -----BEGIN PRIVATE KEY-----
        private-material
        -----END PRIVATE KEY-----
        -----BEGIN PUBLIC KEY-----
        public-material
        -----END PUBLIC KEY-----
        """

        redacted = redactor.redact(source)

        redactor.require_clean(redacted)
        for secret in secrets:
            self.assertNotIn(secret, redacted)
        self.assertNotIn("eyJhbGci", redacted)
        self.assertNotIn("BEGIN PRIVATE KEY", redacted)
        self.assertNotIn("BEGIN PUBLIC KEY", redacted)
        self.assertIn("[REDACTED SECRET]", redacted)
        self.assertIn("[REDACTED JWT]", redacted)
        self.assertIn("[REDACTED PEM]", redacted)

    def test_rejects_unredacted_evidence_and_short_markers(self) -> None:
        secret = "service-secret-value"
        redactor = EvidenceRedactor((secret,))

        with self.assertRaisesRegex(RuntimeError, "exact secret"):
            redactor.require_clean(f"key={secret}")
        with self.assertRaisesRegex(RuntimeError, "key material"):
            redactor.require_clean("-----BEGIN PUBLIC KEY-----x-----END PUBLIC KEY-----")
        with self.assertRaises(ValueError):
            EvidenceRedactor(("short",))


if __name__ == "__main__":
    unittest.main()
