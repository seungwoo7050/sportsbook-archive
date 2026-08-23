import unittest

from tests.secret_fixture import NAMES, VALID, assert_values_redacted, run_preflight


class SecretRequirementsTest(unittest.TestCase):
    def test_accepts_eleven_complete_keys_without_rendering_values(self) -> None:
        result = run_preflight(VALID)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), "secret-preflight: validated 11 distinct keys")
        assert_values_redacted(self, result)

    def test_rejects_each_missing_key_without_rendering_values(self) -> None:
        for name in NAMES:
            with self.subTest(name=name):
                values = VALID.copy()
                del values[name]
                result = run_preflight(values)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(f"{name}: missing", result.stderr)
                assert_values_redacted(self, result)

    def test_rejects_each_short_key_without_rendering_values(self) -> None:
        for name in NAMES:
            with self.subTest(name=name):
                values = VALID | {name: "x" * 31}
                result = run_preflight(values)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(f"{name}: shorter than 32 characters", result.stderr)
                assert_values_redacted(self, result)


if __name__ == "__main__":
    unittest.main()
