from __future__ import annotations
import re
import subprocess
from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.owned_path import require_regular_file
MIN_DLT_RETENTION = 604_800_000
SOURCE_NAMES = frozenset(
    "wallet.debited.v1 wallet.credited.v1 wallet.debit-failed.v1 "
    "risk.limit.violated risk.pattern.suspected odds.changed market.status.changed "
    "event.lifecycle match.result bet.placed.v1 bet.settled.v1 bet.voided.v1 "
    "bet.resolution.revised.v1 admin.action".split()
)
DLT_NAMES = frozenset(
    "wallet.debited.v1.DLT wallet.debit-failed.v1.DLT odds.changed.DLT "
    "event.lifecycle.DLT match.result.DLT bet.placed.v1.DLT bet.settled.v1.DLT "
    "bet.voided.v1.DLT bet.resolution.revised.v1.DLT".split()
)
EXPECTED_NAMES = SOURCE_NAMES | DLT_NAMES
RETENTION = re.compile(r"(?:^|\s)retention\.ms=(\d+)(?=\s|,|$)", re.MULTILINE)

class TopicEvidence:
    def __init__(self, compose: ComposeProject, store: EvidenceStore) -> None:
        if compose.context is not store.context: raise RuntimeError("topic evidence ownership mismatch")
        self.compose = compose
        self.store = store
    def capture(self) -> None:
        rows = self._manifest()
        listed = self._query("kafka-topics.sh", "--list")
        names = [line.strip() for line in listed.splitlines() if line.strip()]
        public = [name for name in names if not name.startswith("__")]
        if len(public) != len(set(public)) or set(public) != EXPECTED_NAMES:
            raise RuntimeError("Kafka topic inventory drifted")
        evidence = ["topic\tpartitions\treplication_factor\tretention_ms"]
        for topic, expected_retention in rows:
            described = self._query("kafka-topics.sh", "--describe", "--topic", topic)
            partitions = self._single(r"\bPartitionCount:\s*(\d+)\b", described)
            replication = self._single(r"\bReplicationFactor:\s*(\d+)\b", described)
            if partitions != 3 or replication != 1:
                raise RuntimeError(f"{topic} partition or replication drifted")
            configured = self._query(
                "kafka-configs.sh", "--entity-type", "topics",
                "--entity-name", topic, "--describe",
            )
            retentions = RETENTION.findall(configured.split("synonyms=", 1)[0])
            if topic in SOURCE_NAMES:
                if retentions:
                    raise RuntimeError(f"{topic} has an undeclared retention override")
                retention = "-"
            else:
                if len(retentions) != 1 or int(retentions[0]) < expected_retention:
                    raise RuntimeError(f"{topic} retention is missing or too short")
                retention = retentions[0]
            evidence.append(f"{topic}\t3\t1\t{retention}")
        self.store.write("topics.tsv", "\n".join(evidence) + "\n")
    def _manifest(self) -> tuple[tuple[str, int], ...]:
        path = self.compose.context.root / "docker/kafka/topics.manifest"
        require_regular_file(path)
        rows = []
        for line in path.read_text().splitlines():
            if not line or line.startswith("#"):
                continue
            fields = line.split("|")
            if len(fields) != 4 or fields[0] not in EXPECTED_NAMES:
                raise RuntimeError("Kafka topic manifest is invalid")
            topic, partitions, replication, retention = fields
            if partitions != "3" or replication != "1":
                raise RuntimeError("Kafka topic manifest topology drifted")
            if topic in SOURCE_NAMES:
                if retention != "-":
                    raise RuntimeError("source topic retention contract drifted")
                minimum = 0
            elif not retention.isdigit() or int(retention) < MIN_DLT_RETENTION:
                raise RuntimeError("DLT retention contract drifted")
            else:
                minimum = int(retention)
            rows.append((topic, minimum))
        if len(rows) != 23 or {topic for topic, _value in rows} != EXPECTED_NAMES:
            raise RuntimeError("Kafka topic manifest inventory drifted")
        return tuple(rows)

    def _query(self, tool: str, *arguments: str) -> str:
        try:
            result = self.compose.run(
                "exec", "-T", "kafka", f"/opt/kafka/bin/{tool}",
                "--bootstrap-server", "kafka:9092", *arguments, capture_output=True,
            )
        except subprocess.CalledProcessError as error:
            raise RuntimeError(f"Kafka evidence query failed: {tool}") from error
        if not isinstance(result.stdout, str):
            raise RuntimeError("Kafka evidence query returned no text")
        return result.stdout

    @staticmethod
    def _single(pattern: str, value: str) -> int:
        matches = re.findall(pattern, value)
        if len(matches) != 1:
            raise RuntimeError("Kafka topic description is malformed")
        return int(matches[0])
