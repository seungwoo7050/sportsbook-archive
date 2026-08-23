from __future__ import annotations

import re
import subprocess

from scripts.cold_gate.compose import ComposeProject


GROUPS = frozenset({"settlement-service", "betting-service", "gateway-service"})
TOPIC = re.compile(r"^[a-z][a-z0-9.-]{1,126}$")


class KafkaAdmin:
    def __init__(self, compose: ComposeProject) -> None:
        self.compose = compose

    def assignments(self, group: str) -> frozenset[tuple[str, int]]:
        if group not in GROUPS:
            raise ValueError("consumer group is outside the release inventory")
        try:
            result = self.compose.run(
                "exec",
                "-T",
                "kafka",
                "/opt/kafka/bin/kafka-consumer-groups.sh",
                "--bootstrap-server",
                "kafka:9092",
                "--describe",
                "--group",
                group,
                capture_output=True,
            )
        except subprocess.CalledProcessError as error:
            raise RuntimeError(f"Kafka group query failed for {group}") from error
        assignments = set()
        for line in result.stdout.splitlines():
            fields = line.split()
            if len(fields) < 3 or fields[0] != group:
                continue
            if TOPIC.fullmatch(fields[1]) is None or not fields[2].isdigit():
                raise RuntimeError("Kafka assignment row is malformed")
            partition = int(fields[2])
            if partition < 0 or partition > 2:
                raise RuntimeError("Kafka assignment partition is out of range")
            pair = (fields[1], partition)
            if pair in assignments:
                raise RuntimeError("Kafka assignment is duplicated")
            assignments.add(pair)
        return frozenset(assignments)
