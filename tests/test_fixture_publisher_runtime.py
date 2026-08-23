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
        self.input_modes = []

    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        mount = next(value for value in arguments if value.endswith(":/fixture.json:ro"))
        input_file = pathlib.Path(mount.split(":", 1)[0])
        self.input_modes.append(input_file.stat().st_mode & 0o777)
        self.payloads.append(json.loads(input_file.read_text()))
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


class PoisonCompose(FakeCompose):
    def run(self, *arguments, **options):
        self.calls.append((arguments, options))
        stdout = (
            f"topic=match.result\tkey={EVENT}\tpartition=2\toffset=9"
            "\tsha256=76be8b528d0075f7aae98d6fa57a6d3c83ae480a8469e668d7b0af968995ac71"
            "\tfingerprint=malformed\n"
        )
        return subprocess.CompletedProcess(arguments, 0, stdout=stdout)


class FailureCompose(FakeCompose):
    def run(self, *arguments, **options):
        raise subprocess.CalledProcessError(1, arguments, stderr="sensitive")


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
            self.assertEqual(compose.input_modes, [0o444])
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

    def test_publishes_only_the_fixed_partition_two_poison_record(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, _compose, jar = self.fixture(pathlib.Path(temporary).resolve())
            compose = PoisonCompose(context)

            receipt = FixturePublisher(context, compose, jar).poison_match_result(EVENT)

            self.assertEqual(receipt.partition, 2)
            self.assertEqual(compose.calls[0][0][-3:], ("poison", "kafka:9092", EVENT))
            self.assertFalse(any("fixture.json" in value for value in compose.calls[0][0]))

    def test_removes_payload_after_a_failed_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            context, _compose, jar = self.fixture(pathlib.Path(temporary).resolve())
            with self.assertRaisesRegex(RuntimeError, "publication failed") as captured:
                FixturePublisher(context, FailureCompose(context), jar).publish(
                    "MatchResult", {"eventId": EVENT}
                )
            self.assertNotIn("sensitive", str(captured.exception))
            self.assertEqual(list((context.runtime / "fixture-inputs").iterdir()), [])


if __name__ == "__main__":
    unittest.main()
