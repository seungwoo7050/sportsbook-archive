from __future__ import annotations

import base64
import dataclasses
import hashlib
import json
import re


TOPIC = re.compile(r"^[a-z][A-Za-z0-9.-]{1,126}$")
HASH = re.compile(r"^[0-9a-f]{64}$")


@dataclasses.dataclass(frozen=True)
class KafkaRecord:
    topic: str
    partition: int
    offset: int
    key: str
    value: bytes
    value_sha256: str
    headers: dict[str, tuple[bytes, ...]]
    avro: dict[str, object] | None

    @classmethod
    def parse(
        cls, output: str, expected_topic: str, expected_partition: int, expected_offset: int
    ) -> "KafkaRecord":
        try:
            payload = json.loads(output)
        except json.JSONDecodeError as error:
            raise RuntimeError("Kafka probe output is not JSON") from error
        if not isinstance(payload, dict):
            raise RuntimeError("Kafka probe output is not an object")
        allowed = {
            "topic", "partition", "offset", "key", "valueBase64",
            "valueSha256", "headers", "avro",
        }
        if set(payload) - allowed:
            raise RuntimeError("Kafka probe output contains unknown fields")
        if (
            payload.get("topic") != expected_topic
            or payload.get("partition") != expected_partition
            or payload.get("offset") != expected_offset
            or TOPIC.fullmatch(expected_topic) is None
        ):
            raise RuntimeError("Kafka probe record identity drifted")
        key = payload.get("key")
        digest = payload.get("valueSha256")
        if not isinstance(key, str) or not key or len(key.encode()) > 128:
            raise RuntimeError("Kafka probe key is invalid")
        if not isinstance(digest, str) or HASH.fullmatch(digest) is None:
            raise RuntimeError("Kafka probe value digest is invalid")
        value = _decode(payload.get("valueBase64"), "Kafka value")
        if hashlib.sha256(value).hexdigest() != digest:
            raise RuntimeError("Kafka probe value digest mismatched")
        raw_headers = payload.get("headers")
        if not isinstance(raw_headers, dict):
            raise RuntimeError("Kafka probe headers are invalid")
        headers = {}
        for name, values in raw_headers.items():
            if not isinstance(name, str) or not name or not isinstance(values, list) or not values:
                raise RuntimeError("Kafka probe header shape is invalid")
            headers[name] = tuple(_decode(value, f"Kafka header {name}") for value in values)
        avro = payload.get("avro")
        if avro is not None and not isinstance(avro, dict):
            raise RuntimeError("Kafka probe Avro payload is invalid")
        return cls(expected_topic, expected_partition, expected_offset, key, value, digest, headers, avro)


def _decode(value: object, name: str) -> bytes:
    if not isinstance(value, str):
        raise RuntimeError(f"{name} is not base64")
    try:
        return base64.b64decode(value, validate=True)
    except (ValueError, base64.binascii.Error) as error:
        raise RuntimeError(f"{name} is not base64") from error
