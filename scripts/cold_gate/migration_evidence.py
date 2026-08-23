from __future__ import annotations

import re
import zlib
from pathlib import Path

from scripts.cold_gate.build import ReleaseArtifacts
from scripts.cold_gate.database import PostgresClient
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.inventory import MIGRATION_VERSIONS
from scripts.cold_gate.owned_path import require_directory, require_regular_file


MIGRATION_NAME = re.compile(r"^V([1-9][0-9]*)__([A-Za-z0-9_]+)\.sql$")
HISTORY_QUERY = (
    "SELECT installed_rank::text AS installed_rank, version, script, "
    "checksum::text AS checksum, success::text AS success "
    "FROM flyway_schema_history ORDER BY flyway_schema_history.installed_rank"
)


def flyway_checksum(path: Path) -> int:
    require_regular_file(path)
    try:
        text = path.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError as error:
        raise RuntimeError(f"{path.name} is not UTF-8") from error
    checksum = 0
    for line in text.splitlines():
        checksum = zlib.crc32(line.encode("utf-8"), checksum)
    return checksum - (1 << 32) if checksum >= (1 << 31) else checksum


class MigrationEvidence:
    def __init__(
        self,
        artifacts: ReleaseArtifacts,
        database: PostgresClient,
        store: EvidenceStore,
    ) -> None:
        if artifacts.sources != store.context.runtime / "sources":
            raise RuntimeError("migration evidence ownership mismatch")
        self.artifacts = artifacts
        self.database = database
        self.store = store

    def capture(self) -> None:
        self.store.context.require_owned()
        evidence = ["database\tinstalled_rank\tversion\tscript\tchecksum\tsuccess"]
        for database, versions in MIGRATION_VERSIONS.items():
            expected = self._source_history(database, versions)
            observed = self.database.query(database, HISTORY_QUERY)
            if observed != expected:
                raise RuntimeError(f"{database} Flyway history drifted")
            for row in observed:
                evidence.append(
                    "\t".join(
                        [
                            database,
                            row["installed_rank"],
                            row["version"],
                            row["script"],
                            row["checksum"],
                            row["success"],
                        ]
                    )
                )
        if len(evidence) != 26:
            raise RuntimeError("release migration inventory is not exactly 25 rows")
        self.store.write("migrations.tsv", "\n".join(evidence) + "\n")

    def _source_history(
        self, database: str, versions: tuple[str, ...]
    ) -> list[dict[str, str]]:
        directory = (
            self.artifacts.sources / database / "src/main/resources/db/migration"
        )
        require_directory(directory)
        files: dict[str, Path] = {}
        for path in directory.iterdir():
            require_regular_file(path)
            match = MIGRATION_NAME.fullmatch(path.name)
            if match is None or match.group(1) in files:
                raise RuntimeError(f"{database} migration source inventory is invalid")
            files[match.group(1)] = path
        if set(files) != set(versions):
            raise RuntimeError(f"{database} migration source versions drifted")
        return [
            {
                "installed_rank": str(rank),
                "version": version,
                "script": files[version].name,
                "checksum": str(flyway_checksum(files[version])),
                "success": "true",
            }
            for rank, version in enumerate(versions, 1)
        ]
