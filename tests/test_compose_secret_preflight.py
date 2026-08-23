from tests.compose_contract_fixture import ComposeContractFixture
from tests.secret_fixture import VALID


class ComposeSecretPreflightTest(ComposeContractFixture):
    def test_runs_before_services_without_network_or_value_disclosure(self) -> None:
        preflight = self.service("secret-preflight")
        self.assertEqual(preflight["network_mode"], "none")
        self.assertNotIn("networks", preflight)

        result = self.compose("run", "--rm", "--no-deps", "secret-preflight")

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("validated 11 distinct keys", result.stdout)
        output = result.stdout + result.stderr
        for value in VALID.values():
            self.assertNotIn(value, output)


if __name__ == "__main__":
    import unittest

    unittest.main()
