import itertools
import unittest

from tests.secret_fixture import NAMES, VALID, assert_values_redacted, run_preflight


class SecretDistinctnessTest(unittest.TestCase):
    def test_rejects_every_pairwise_duplicate_without_rendering_values(self) -> None:
        for left, right in itertools.combinations(NAMES, 2):
            with self.subTest(left=left, right=right):
                values = VALID | {right: VALID[left]}
                result = run_preflight(values)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(f"{left} and {right}: values must differ", result.stderr)
                assert_values_redacted(self, result)


if __name__ == "__main__":
    unittest.main()
