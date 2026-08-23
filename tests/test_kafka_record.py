import base64
import hashlib
import json
import unittest

from scripts.cold_gate.kafka_record import KafkaRecord


def encoded(value: bytes) -> str:
    return base64.b64encode(value).decode()


class KafkaRecordTest(unittest.TestCase):
    def payload(self) -> dict[str, object]:
        value = b"\x80"
        return {
            "topic": "match.result.DLT",
            "partition": 2,
            "offset": 7,
            "key": "11000000-0000-7000-8000-000000000012",
            "valueBase64": encoded(value),
            "valueSha256": hashlib.sha256(value).hexdigest(),
            "headers": {
                "original-topic": [encoded(b"match.result")],
                "original-partition": [encoded((2).to_bytes(4, "big"))],
            },
            "avro": {"status": "SUCCESS"},
        }

    def test_verifies_raw_value_headers_and_optional_avro(self) -> None:
        record = KafkaRecord.parse(
            json.dumps(self.payload()), "match.result.DLT", 2, 7
        )

        self.assertEqual(record.value, b"\x80")
        self.assertEqual(record.key, "11000000-0000-7000-8000-000000000012")
        self.assertEqual(record.headers["original-topic"], (b"match.result",))
        self.assertEqual(
            record.headers["original-partition"], ((2).to_bytes(4, "big"),)
        )
        self.assertEqual(record.avro, {"status": "SUCCESS"})

    def test_rejects_identity_digest_and_base64_drift(self) -> None:
        mutations = []
        wrong_topic = self.payload()
        wrong_topic["topic"] = "other.DLT"
        mutations.append(wrong_topic)
        wrong_hash = self.payload()
        wrong_hash["valueSha256"] = "0" * 64
        mutations.append(wrong_hash)
        wrong_base64 = self.payload()
        wrong_base64["valueBase64"] = "!"
        mutations.append(wrong_base64)
        unknown = self.payload()
        unknown["credential"] = "forbidden"
        mutations.append(unknown)

        for payload in mutations:
            with self.subTest(payload=payload):
                with self.assertRaises(RuntimeError):
                    KafkaRecord.parse(json.dumps(payload), "match.result.DLT", 2, 7)

    def test_rejects_empty_or_ambiguous_header_shapes(self) -> None:
        payload = self.payload()
        payload["headers"] = {"original-topic": []}
        with self.assertRaisesRegex(RuntimeError, "header shape"):
            KafkaRecord.parse(json.dumps(payload), "match.result.DLT", 2, 7)


if __name__ == "__main__":
    unittest.main()
