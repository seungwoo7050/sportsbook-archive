from __future__ import annotations

import subprocess
from pathlib import Path

from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.kafka_record import KafkaRecord, TOPIC
from scripts.cold_gate.owned_path import require_regular_file


PROBE_CLASS = "com.sportsbook.orchestration.fixture.KafkaProbe"


class KafkaProbe:
    def __init__(
        self, context: ColdGateContext, compose: ComposeProject, fixture_jar: Path
    ) -> None:
        expected = context.runtime / "fixtures/avro-fixture-publisher.jar"
        if compose.context is not context or fixture_jar != expected:
            raise RuntimeError("Kafka probe is not owned by this cold gate")
        context.require_owned()
        require_regular_file(fixture_jar)
        self.context = context
        self.compose = compose
        self.fixture_jar = fixture_jar

    def read(
        self,
        topic: str,
        partition: int,
        offset: int,
        schema: Path | None = None,
    ) -> KafkaRecord:
        if TOPIC.fullmatch(topic) is None or partition not in range(3) or offset < 0:
            raise ValueError("Kafka probe target is outside the gate contract")
        command = [
            "run", "--rm", "--no-deps", "--entrypoint", "java",
            "--volume", f"{self.fixture_jar}:/fixture.jar:ro",
        ]
        schema_argument = []
        if schema is not None:
            require_regular_file(schema)
            source_root = self.context.runtime / "sources"
            if not schema.is_relative_to(source_root):
                raise RuntimeError("Kafka probe schema is outside locked sources")
            command.extend(("--volume", f"{schema}:/fixture.avsc:ro"))
            schema_argument = ["/fixture.avsc"]
        command.extend(
            (
                "wallet", "-cp", "/fixture.jar", PROBE_CLASS,
                "kafka:9092", topic, str(partition), str(offset), *schema_argument,
            )
        )
        try:
            result = self.compose.run(*command, capture_output=True)
        except subprocess.CalledProcessError as error:
            raise RuntimeError("Kafka record probe failed") from error
        return KafkaRecord.parse(result.stdout, topic, partition, offset)
