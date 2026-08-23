from __future__ import annotations

import hashlib
import json
import subprocess
import tempfile
import uuid
from pathlib import Path

from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.fixture_receipt import FixtureReceipt
from scripts.cold_gate.owned_path import ensure_directory, require_regular_file


FIXTURE_TYPES = {
    "EventLifecycle": ("event.lifecycle", "e47d6dbd952bc721"),
    "MatchResult": ("match.result", "3f39fbc4bbfea727"),
    "BetSettled": ("bet.settled.v1", "113bc9d5037a850c"),
    "BetResolutionRevised": ("bet.resolution.revised.v1", "b05cdf4b95651059"),
}


class FixturePublisher:
    def __init__(
        self, context: ColdGateContext, compose: ComposeProject, fixture_jar: Path
    ) -> None:
        expected = context.runtime / "fixtures/avro-fixture-publisher.jar"
        if fixture_jar != expected or compose.context is not context:
            raise RuntimeError("fixture publisher is not owned by this cold gate")
        context.require_owned()
        require_regular_file(fixture_jar)
        self.context = context
        self.compose = compose
        self.fixture_jar = fixture_jar

    def publish(
        self, fixture_type: str, payload: dict[str, object], partition: int | None = None
    ) -> FixtureReceipt:
        if fixture_type not in FIXTURE_TYPES or partition not in (None, 0, 1, 2):
            raise ValueError("fixture publication is outside the schema contract")
        inputs = self.context.runtime / "fixture-inputs"
        ensure_directory(inputs)
        pending: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w", dir=inputs, prefix="fixture.", suffix=".json", delete=False
            ) as output:
                json.dump(payload, output, sort_keys=True, separators=(",", ":"))
                pending = Path(output.name)
            pending.chmod(0o600)
            arguments = ["publish", "kafka:9092", fixture_type, "/fixture.json"]
            if partition is not None:
                arguments.append(str(partition))
            receipt = self._run(arguments, pending)
        finally:
            if pending is not None:
                pending.unlink(missing_ok=True)
        topic, fingerprint = FIXTURE_TYPES[fixture_type]
        if receipt.topic != topic or receipt.fingerprint != fingerprint:
            raise RuntimeError("fixture publisher returned the wrong schema identity")
        if partition is not None and receipt.partition != partition:
            raise RuntimeError("fixture publisher returned the wrong partition")
        return receipt

    def poison_match_result(self, event_id: str) -> FixtureReceipt:
        parsed = uuid.UUID(event_id)
        if str(parsed) != event_id:
            raise ValueError("poison event ID must be canonical")
        receipt = self._run(["poison", "kafka:9092", event_id])
        poison_hash = hashlib.sha256(b"\x80").hexdigest()
        if (
            receipt.topic != "match.result"
            or receipt.key != event_id
            or receipt.partition != 2
            or receipt.sha256 != poison_hash
            or receipt.fingerprint != "malformed"
        ):
            raise RuntimeError("poison publisher returned the wrong record identity")
        return receipt

    def _run(self, arguments: list[str], input_file: Path | None = None) -> FixtureReceipt:
        command = [
            "run", "--rm", "--no-deps", "--entrypoint", "java",
            "--volume", f"{self.fixture_jar}:/fixture.jar:ro",
        ]
        if input_file is not None:
            require_regular_file(input_file)
            command.extend(("--volume", f"{input_file}:/fixture.json:ro"))
        command.extend(("wallet", "-jar", "/fixture.jar", *arguments))
        try:
            result = self.compose.run(*command, capture_output=True)
        except subprocess.CalledProcessError as error:
            raise RuntimeError("fixture publication failed") from error
        return FixtureReceipt.parse(result.stdout)
