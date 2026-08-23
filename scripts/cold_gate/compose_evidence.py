from __future__ import annotations

import hashlib
import json
from collections.abc import Iterable

from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.inventory import SERVICES


def capture_compose_config(
    compose: ComposeProject,
    store: EvidenceStore,
    environment: dict[str, str],
    secret_values: Iterable[str],
) -> str:
    if environment.get("COMPOSE_PROJECT_NAME") != compose.context.project:
        raise RuntimeError("Compose evidence environment owns another project")
    result = compose.run(
        "config",
        "--no-interpolate",
        "--format",
        "json",
        environment=environment,
        capture_output=True,
    )
    if any(secret and secret in result.stdout for secret in secret_values):
        raise RuntimeError("rendered Compose config contains a runtime secret")
    try:
        config = json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise RuntimeError("rendered Compose config is not JSON") from error
    if (
        not isinstance(config, dict)
        or config.get("name") != compose.context.project
        or not isinstance(config.get("services"), dict)
        or set(config["services"]) != set(SERVICES)
    ):
        raise RuntimeError("rendered Compose inventory drifted")
    canonical = json.dumps(config, sort_keys=True, separators=(",", ":")).encode()
    digest = hashlib.sha256(canonical).hexdigest()
    store.write("compose.sha256", f"artifact\tsha256\ncombined-config\t{digest}\n")
    return digest
