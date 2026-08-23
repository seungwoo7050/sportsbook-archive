import subprocess
import unittest

from scripts.cold_gate.database import PostgresClient, uuid_literal


class FakeCompose:
    def __init__(self, output: str, failure: bool = False) -> None:
        self.output = output
        self.failure = failure
        self.calls = []

    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        if self.failure:
            raise subprocess.CalledProcessError(2, arguments, stderr="credential")
        return subprocess.CompletedProcess(arguments, 0, stdout=self.output)


class ColdGateDatabaseTest(unittest.TestCase):
    def test_parses_named_csv_rows_from_the_exact_database(self) -> None:
        compose = FakeCompose('status,detail\nACCEPTED,"safe, value"\n')

        rows = PostgresClient(compose).query(
            "betting", "SELECT status, detail FROM bet WHERE bet_id = 'fixture'"
        )

        arguments, options = compose.calls[0]
        self.assertEqual(arguments[:4], ("exec", "-T", "postgres", "psql"))
        self.assertEqual(arguments[arguments.index("--dbname") + 1], "betting")
        self.assertIn("--csv", arguments)
        self.assertEqual(options, {"capture_output": True})
        self.assertEqual(rows, [{"status": "ACCEPTED", "detail": "safe, value"}])

    def test_requires_exact_scalar_cardinality(self) -> None:
        self.assertEqual(PostgresClient(FakeCompose("count\n1\n")).scalar("wallet", "SELECT 1"), "1")
        with self.assertRaisesRegex(RuntimeError, "one PostgreSQL row"):
            PostgresClient(FakeCompose("count\n1\n2\n")).one("wallet", "SELECT 1")
        with self.assertRaisesRegex(RuntimeError, "one PostgreSQL column"):
            PostgresClient(FakeCompose("a,b\n1,2\n")).scalar("wallet", "SELECT 1, 2")

    def test_rejects_unowned_or_multi_statement_queries(self) -> None:
        client = PostgresClient(FakeCompose("value\n1\n"))
        with self.assertRaisesRegex(ValueError, "outside the release"):
            client.query("postgres", "SELECT 1")
        for statement in ("DELETE FROM bet", "SELECT 1; SELECT 2", "\\copy secret"):
            with self.subTest(statement=statement):
                with self.assertRaisesRegex(ValueError, "statement"):
                    client.query("betting", statement)

    def test_formats_only_canonical_uuid_literals(self) -> None:
        value = "01000000-0000-7000-8000-000000000001"
        self.assertEqual(uuid_literal(value), f"'{value}'::uuid")
        with self.assertRaises(ValueError):
            uuid_literal("not-a-uuid")

    def test_hides_database_transport_output(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "wallet") as captured:
            PostgresClient(FakeCompose("", failure=True)).query("wallet", "SELECT 1")
        self.assertNotIn("credential", str(captured.exception))


if __name__ == "__main__":
    unittest.main()
