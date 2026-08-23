import json

from tests.compose_fixture import ComposeFixture
from tests.secret_fixture import VALID


PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\ncontract-public-key\n-----END PUBLIC KEY-----"


class ComposeContractFixture(ComposeFixture):
    def setUp(self) -> None:
        super().setUp()
        self.environment.update(VALID)
        self.environment["GATEWAY_JWT_PUBLIC_KEY"] = PUBLIC_KEY
        self.environment["ADMIN_JWT_PUBLIC_KEY"] = PUBLIC_KEY
        self.environment["ADMIN_JWT_ISSUER"] = "sportsbook-admin-e2e"

    def rendered(self) -> dict:
        result = self.compose("config", "--format", "json")
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def service(self, name: str) -> dict:
        return self.rendered()["services"][name]

    def assert_runtime_build(self, service: dict, jar: str) -> None:
        self.assertTrue(service["build"]["context"].endswith("/docker"))
        self.assertEqual(service["build"]["dockerfile"], "Dockerfile.jvm")
        self.assertEqual(service["build"]["args"], {"JAR": jar})

    def assert_dependency_conditions(self, service: dict, expected: dict[str, str]) -> None:
        actual = {
            name: dependency["condition"]
            for name, dependency in service["depends_on"].items()
        }
        self.assertEqual(actual, expected)
