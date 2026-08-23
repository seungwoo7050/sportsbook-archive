from __future__ import annotations

import re
import subprocess

from scripts.cold_gate.compose import ComposeProject


GROUPS = frozenset(
    {"settlement-service", "betting-resolution", "betting-wallet", "gateway-bets", "gateway-odds"}
)
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

    def committed_offset(self, group: str, topic: str, partition: int) -> int:
        if group not in GROUPS or TOPIC.fullmatch(topic) is None or partition not in range(3):
            raise ValueError("consumer offset target is outside the release inventory")
        try:
            result = self.compose.run(
                "exec", "-T", "kafka", "/opt/kafka/bin/kafka-consumer-groups.sh",
                "--bootstrap-server", "kafka:9092", "--describe", "--group", group,
                capture_output=True,
            )
        except subprocess.CalledProcessError as error:
            raise RuntimeError(f"Kafka offset query failed for {group}") from error
        offsets = []
        for line in result.stdout.splitlines():
            fields = line.split()
            if len(fields) >= 4 and fields[:3] == [group, topic, str(partition)]:
                if not fields[3].isdigit():
                    raise RuntimeError("Kafka committed offset is unavailable")
                offsets.append(int(fields[3]))
        if len(offsets) != 1:
            raise RuntimeError("Kafka committed offset row is not unique")
        return offsets[0]

    def topic_lag(self, group: str, topic: str) -> int:
        if group not in GROUPS or TOPIC.fullmatch(topic) is None:
            raise ValueError("consumer lag target is outside the release inventory")
        try:
            result = self.compose.run(
                "exec", "-T", "kafka", "/opt/kafka/bin/kafka-consumer-groups.sh",
                "--bootstrap-server", "kafka:9092", "--describe", "--group", group,
                capture_output=True,
            )
        except subprocess.CalledProcessError as error:
            raise RuntimeError(f"Kafka lag query failed for {group}") from error
        lags = []
        for line in result.stdout.splitlines():
            fields = line.split()
            if len(fields) >= 6 and fields[:2] == [group, topic]:
                if not fields[2].isdigit() or not fields[5].isdigit():
                    raise RuntimeError("Kafka lag row is malformed")
                lags.append((int(fields[2]), int(fields[5])))
        if {partition for partition, _lag in lags} != {0, 1, 2}:
            raise RuntimeError("Kafka topic lag inventory is incomplete")
        return sum(lag for _partition, lag in lags)
