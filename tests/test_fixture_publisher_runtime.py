import json
import pathlib
import subprocess
import tempfile
import unittest

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.fixtures import FixturePublisher


SHA = "0123456789abcdef0123456789abcdef01234567"
EVENT = "11000000-0000-7000-8000-000000000001"


class FakeCompose:
    def __init__(self, context: ColdGateContext) -> None:
        self.context = context
        self.calls = []
        self.payloads = []

    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        mount = next(value for value in arguments if value.endswith(":/fixture.json:ro"))
        self.payloads.append(json.loads(pathlib.Path(mount.split(":", 1)[0]).read_text()))
        fixture_type = arguments[arguments.index("publish") + 2]
        topic, fingerprint = {
            "MatchResult": ("match.result", "3f39fbc4bbfea727"),
            "EventLifecycle": ("event.lifecycle", "e47d6dbd952bc721"),
        }[fixture_type]
        partition = arguments[-1] if arguments[-1] in {"0", "1", "2"} else "0"
        stdout = (
            f"topic={topic}\tkey={EVENT}\tpartition={partition}\toffset=4"
            f"\tsha256={'a' * 64}\tfingerprint={fingerprint}\n"
        )
        return subprocess.CompletedProcess(arguments, 0, stdout=stdout)


class FixturePublisherRuntimeTest(unittest.TestCase):
    def fixture(self, root: pathlib.Path):
        context = ColdGateContext.create(root, SHA, "00000001")
        fixture_dir = context.runtime / "fixtures"
        fixture_dir.mkdir()
        jar = fixture_dir / "avro-fixture-publisher.jar"
        jar.write_bytes(b"fixture")
        compose = FakeCompose(context)
        return context, compose, jar

    def test_publishes_canonical_json_with_the_staged_tool(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, compose, jar = self.fixture(pathlib.Path(temporary).resolve())
            publisher = FixturePublisher(context, compose, jar)

            receipt = publisher.publish(
                "MatchResult", {"settledAt": 1, "eventId": EVENT, "resultDetail": {}}
            )

            arguments, options = compose.calls[0]
            self.assertEqual(arguments[:6], ("run", "--rm", "--no-deps", "--entrypoint", "java", "--volume"))
            self.assertIn(f"{jar}:/fixture.jar:ro", arguments)
            self.assertEqual(arguments[-4:], ("publish", "kafka:9092", "MatchResult", "/fixture.json"))
            self.assertEqual(options, {"capture_output": True})
            self.assertEqual(compose.payloads[0]["eventId"], EVENT)
            self.assertEqual(receipt.topic, "match.result")
            self.assertEqual(list((context.runtime / "fixture-inputs").iterdir()), [])

    def test_pins_an_explicit_partition_and_rejects_unknown_schemas(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, compose, jar = self.fixture(pathlib.Path(temporary).resolve())
            publisher = FixturePublisher(context, compose, jar)
            receipt = publisher.publish("EventLifecycle", {"eventId": EVENT}, partition=1)
            self.assertEqual(receipt.partition, 1)
            with self.assertRaisesRegex(ValueError, "schema contract"):
                publisher.publish("Unknown", {"eventId": EVENT})


if __name__ == "__main__":
    unittest.main()
