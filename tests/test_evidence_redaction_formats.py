import unittest

from scripts.cold_gate.redaction import EvidenceRedactor


class EvidenceRedactionFormatsTest(unittest.TestCase):
    def test_redacts_json_url_and_opaque_authorization_values(self) -> None:
        secret = 'quoted-"secret"-value'
        redactor = EvidenceRedactor((secret,))
        source = '''
        {"x-api-key":"quoted-\\"secret\\"-value",
         "url":"quoted-%22secret%22-value",
         "access_token":"unregistered-opaque-token",
         "Authorization":"Bearer opaque.header.signature"}
        Proxy-Authorization: Basic opaque-credential
        sessionToken=another-opaque-value
        '''

        redacted = redactor.redact(source)

        redactor.require_clean(redacted)
        self.assertNotIn("quoted", redacted)
        self.assertNotIn("unregistered-opaque-token", redacted)
        self.assertNotIn("opaque.header.signature", redacted)
        self.assertNotIn("opaque-credential", redacted)
        self.assertNotIn("another-opaque-value", redacted)

    def test_post_scan_rejects_unknown_bearer_and_literal_newline_pem(self) -> None:
        redactor = EvidenceRedactor(("registered-secret-value",))

        with self.assertRaisesRegex(RuntimeError, "credential material"):
            redactor.require_clean("Authorization: Bearer opaque-token-value")
        with self.assertRaisesRegex(RuntimeError, "key material"):
            redactor.require_clean(
                r"-----BEGIN PRIVATE KEY-----\nmaterial\n-----END PRIVATE KEY-----"
            )


if __name__ == "__main__":
    unittest.main()
