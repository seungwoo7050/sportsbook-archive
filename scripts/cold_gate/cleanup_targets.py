from __future__ import annotations

import os
from pathlib import Path, PurePosixPath

from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.owned_path import require_directory


def discover_cleanup_targets(
    context: ColdGateContext,
) -> tuple[Path | None, Path | None]:
    context.require_owned()
    sources = context.runtime / "sources"
    if sources.is_symlink() or (sources.exists() and not sources.is_dir()):
        raise RuntimeError("materialized source target is not an owned directory")
    source_target = sources if sources.is_dir() else None

    jars_parent = context.root / "docker/.jars"
    jars_link = context.root / "docker/jars"
    if jars_link.exists() and not jars_link.is_symlink():
        raise RuntimeError("release JAR link is not an owned symlink")
    if jars_link.is_symlink():
        relative = PurePosixPath(os.readlink(jars_link))
        if (
            relative.is_absolute()
            or len(relative.parts) != 2
            or relative.parts[0] != ".jars"
            or relative.parts[1] in ("", ".", "..")
        ):
            raise RuntimeError("release JAR link escaped its owned generation")
        service_jars = jars_parent / relative.parts[1]
        require_directory(service_jars)
        generations = list(jars_parent.iterdir())
        if generations != [service_jars]:
            raise RuntimeError("release JAR generation inventory is ambiguous")
        return source_target, service_jars
    if jars_parent.exists() or jars_parent.is_symlink():
        raise RuntimeError("release JAR generation exists without its owned link")
    return source_target, None
