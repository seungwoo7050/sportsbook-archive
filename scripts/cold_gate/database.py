from __future__ import annotations

import csv
import io
import re
import subprocess
import uuid

from scripts.cold_gate.compose import ComposeProject


DATABASES = frozenset({"wallet", "betting", "settlement", "admin"})


class PostgresClient:
    def __init__(self, compose: ComposeProject) -> None:
        self.compose = compose

    def query(self, database: str, statement: str) -> list[dict[str, str]]:
        if database not in DATABASES:
            raise ValueError("database is outside the release inventory")
        normalized = statement.strip()
        if (
            not re.match(r"^(SELECT|WITH|UPDATE)\b", normalized, re.IGNORECASE)
            or "\0" in normalized
            or ";" in normalized
            or len(normalized.encode()) > 16_384
        ):
            raise ValueError("database statement is outside the gate contract")
        try:
            result = self.compose.run(
                "exec",
                "-T",
                "postgres",
                "psql",
                "--no-psqlrc",
                "--set",
                "ON_ERROR_STOP=1",
                "--username",
                "sportsbook",
                "--dbname",
                database,
                "--csv",
                "--command",
                normalized,
                capture_output=True,
            )
        except subprocess.CalledProcessError as error:
            raise RuntimeError(f"PostgreSQL query failed for {database}") from error
        output = result.stdout
        if normalized.upper().startswith("UPDATE"):
            output = re.sub(r"\nUPDATE [0-9]+\n?\Z", "", output)
        reader = csv.DictReader(io.StringIO(output))
        if reader.fieldnames is None or len(reader.fieldnames) != len(set(reader.fieldnames)):
            raise RuntimeError("PostgreSQL result columns are invalid")
        return [dict(row) for row in reader]

    def one(self, database: str, statement: str) -> dict[str, str]:
        rows = self.query(database, statement)
        if len(rows) != 1:
            raise RuntimeError(f"expected one PostgreSQL row, observed {len(rows)}")
        return rows[0]

    def scalar(self, database: str, statement: str) -> str:
        row = self.one(database, statement)
        if len(row) != 1:
            raise RuntimeError("expected one PostgreSQL column")
        return next(iter(row.values()))


def uuid_literal(value: str) -> str:
    parsed = uuid.UUID(value)
    if str(parsed) != value:
        raise ValueError("SQL UUID must be canonical")
    return f"'{value}'::uuid"
