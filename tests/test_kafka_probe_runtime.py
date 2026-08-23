import base64
import hashlib
import json
import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.probe import KafkaProbe, PROBE_CLASS


SHA = "0123456789abcdef0123456789abcdef01234567"


class FakeCompose:
    def __init__(self, context: ColdGateContext, failure: bool = False) -> None:
        self.context = context
        self.failure = failure
        self.calls = []

    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        if self.failure:
            raise subprocess.CalledProcessError(1, arguments, stderr="sensitive")
        value = b"record"
        output = json.dumps(
            {
                "topic": "admin.action",
                "partition": 1,
                "offset": 4,
                "key": "e2e-admin",
                "valueBase64": base64.b64encode(value).decode(),
                "valueSha256": hashlib.sha256(value).hexdigest(),
                "headers": {"event-id": [base64.b64encode(b"id").decode()]},
                "avro": {"outcome": "SUCCESS"},
            }
        )
        return subprocess.CompletedProcess(arguments, 0, stdout=output)


class KafkaProbeRuntimeTest(unittest.TestCase):
    def fixture(self, root: pathlib.Path):
        context = ColdGateContext.create(root, SHA, "00000001")
        fixture_dir = context.runtime / "fixtures"
        fixture_dir.mkdir()
        jar = fixture_dir / "avro-fixture-publisher.jar"
        jar.write_bytes(b"fixture")
        schema_dir = context.runtime / "sources/admin/src/main/avro"
        schema_dir.mkdir(parents=True)
        schema = schema_dir / "AdminActionRecorded.avsc"
        schema.write_text("{}")
        return context, jar, schema

    def test_runs_the_staged_probe_with_one_locked_schema(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, jar, schema = self.fixture(pathlib.Path(temporary).resolve())
            compose = FakeCompose(context)

            record = KafkaProbe(context, compose, jar).read(
                "admin.action", 1, 4, schema
            )

            arguments, options = compose.calls[0]
            self.assertEqual(arguments[:5], ("run", "--rm", "--no-deps", "--entrypoint", "java"))
            self.assertIn(f"{jar}:/fixture.jar:ro", arguments)
            self.assertIn(f"{schema}:/fixture.avsc:ro", arguments)
            self.assertIn(PROBE_CLASS, arguments)
            self.assertEqual(arguments[-5:], ("kafka:9092", "admin.action", "1", "4", "/fixture.avsc"))
            self.assertEqual(options, {"capture_output": True})
            self.assertEqual(record.key, "e2e-admin")
            self.assertEqual(record.avro, {"outcome": "SUCCESS"})

    def test_rejects_external_schemas_and_hides_probe_failures(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary).resolve()
            context, jar, _schema = self.fixture(root)
            external = root / "external.avsc"
            external.write_text("{}")
            with self.assertRaisesRegex(RuntimeError, "outside locked"):
                KafkaProbe(context, FakeCompose(context), jar).read("admin.action", 1, 4, external)
            with self.assertRaisesRegex(RuntimeError, "probe failed") as captured:
                KafkaProbe(context, FakeCompose(context, failure=True), jar).read(
                    "match.result.DLT", 2, 0
                )
            self.assertNotIn("sensitive", str(captured.exception))


if __name__ == "__main__":
    unittest.main()
