import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.redaction import EvidenceRedactor
from scripts.cold_gate.topic_evidence import DLT_NAMES, EXPECTED_NAMES, TopicEvidence


ROOT = pathlib.Path(__file__).resolve().parents[1]
SHA = "0123456789abcdef0123456789abcdef01234567"
SECRET = "topic-evidence-secret-000000000000"


class FakeCompose:
    def __init__(self, context: ColdGateContext, fault: str = "") -> None:
        self.context = context
        self.fault = fault
        self.calls = []

    def run(self, *arguments, **_options):
        self.calls.append(arguments)
        program = arguments[3]
        if program.endswith("kafka-topics.sh") and "--list" in arguments:
            names = sorted(EXPECTED_NAMES)
            if self.fault == "extra":
                names.append("undeclared.topic")
            output = "__consumer_offsets\n" + "\n".join(names) + "\n"
        elif program.endswith("kafka-topics.sh"):
            topic = arguments[-1]
            partitions = 2 if self.fault == "partition" and topic == "admin.action" else 3
            output = (
                f"Topic: {topic} TopicId: id PartitionCount: {partitions} "
                "ReplicationFactor: 1 Configs:\n"
            )
        else:
            topic = arguments[arguments.index("--entity-name") + 1]
            if topic in DLT_NAMES:
                retention = 60_000 if self.fault == "short" else 604_800_000
                output = (
                    f"Dynamic configs for topic {topic} are: retention.ms={retention} "
                    f"sensitive=false synonyms={{DYNAMIC_TOPIC_CONFIG:retention.ms={retention}, "
                    "DEFAULT_CONFIG:log.retention.ms=null}}\n"
                )
            elif self.fault == "source-retention" and topic == "admin.action":
                output = "Dynamic configs for topic admin.action are: retention.ms=1\n"
            else:
                output = f"Dynamic configs for topic {topic} are:\n"
        return subprocess.CompletedProcess(arguments, 0, stdout=output)


class TopicEvidenceTest(unittest.TestCase):
    def fixture(self, root: pathlib.Path, fault: str = ""):
        manifest = (ROOT / "docker/kafka/topics.manifest").read_text()
        target = root / "docker/kafka"
        target.mkdir(parents=True)
        if fault == "manifest":
            manifest = manifest.replace("admin.action|3|1|-\n", "")
        (target / "topics.manifest").write_text(manifest)
        context = ColdGateContext.create(root, SHA, "00000001")
        store = EvidenceStore(context, EvidenceRedactor([SECRET]))
        return context, store, FakeCompose(context, fault)

    def test_records_the_exact_validated_broker_inventory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, store, compose = self.fixture(pathlib.Path(temporary).resolve())

            TopicEvidence(compose, store).capture()

            lines = (context.evidence / "topics.tsv").read_text().splitlines()
            self.assertEqual(lines[0], "topic\tpartitions\treplication_factor\tretention_ms")
            self.assertEqual(len(lines), 24)
            rows = {line.split("\t")[0]: line.split("\t")[1:] for line in lines[1:]}
            self.assertEqual(set(rows), EXPECTED_NAMES)
            self.assertTrue(all(row[:2] == ["3", "1"] for row in rows.values()))
            self.assertTrue(all(rows[name][2] == "-" for name in EXPECTED_NAMES - DLT_NAMES))
            self.assertTrue(all(int(rows[name][2]) >= 604_800_000 for name in DLT_NAMES))
            self.assertEqual(len(compose.calls), 47)

    def test_rejects_manifest_or_broker_drift_before_writing(self) -> None:
        faults = {
            "manifest": "manifest inventory",
            "extra": "inventory drifted",
            "partition": "partition or replication",
            "short": "retention is missing or too short",
            "source-retention": "undeclared retention override",
        }
        for fault, message in faults.items():
            with self.subTest(fault=fault), tempfile.TemporaryDirectory() as temporary:
                context, store, compose = self.fixture(pathlib.Path(temporary).resolve(), fault)
                with self.assertRaisesRegex(RuntimeError, message):
                    TopicEvidence(compose, store).capture()
                self.assertEqual(list(context.evidence.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
