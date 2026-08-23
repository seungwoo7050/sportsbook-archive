from tests.compose_fixture import ComposeFixture


class PostgresBootstrapTest(ComposeFixture):
    def test_bootstraps_exactly_the_four_service_databases(self) -> None:
        started = self.compose("up", "--detach", "--wait", "postgres")
        self.assertEqual(started.returncode, 0, started.stderr)

        query = self.compose(
            "exec",
            "--no-TTY",
            "postgres",
            "psql",
            "--username",
            "sportsbook",
            "--dbname",
            "postgres",
            "--tuples-only",
            "--no-align",
            "--field-separator",
            "|",
            "--command",
            "SELECT datname, pg_get_userbyid(datdba) FROM pg_database "
            "WHERE datallowconn AND NOT datistemplate AND datname <> 'postgres' "
            "ORDER BY datname",
        )

        self.assertEqual(query.returncode, 0, query.stderr)
        self.assertEqual(
            query.stdout.splitlines(),
            [
                "admin|sportsbook",
                "betting|sportsbook",
                "settlement|sportsbook",
                "wallet|sportsbook",
            ],
        )


if __name__ == "__main__":
    import unittest

    unittest.main()
