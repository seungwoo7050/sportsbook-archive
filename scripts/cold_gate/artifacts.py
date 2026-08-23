from __future__ import annotations

import hashlib
import re
import zipfile
from pathlib import Path

from scripts.cold_gate.owned_path import require_directory, require_regular_file


SERVICES = ("wallet", "risk", "odds", "betting", "gateway", "settlement", "admin")
SUM_PATTERN = re.compile(r"^([0-9a-f]{64})  ([a-z]+\.jar)$")


def verify_release_artifacts(directory: Path) -> dict[str, str]:
    require_directory(directory)
    expected = {f"{service}.jar" for service in SERVICES}
    sums_file = directory / "SHA256SUMS"
    require_regular_file(sums_file)
    if {path.name for path in directory.iterdir()} != expected | {"SHA256SUMS"}:
        raise RuntimeError("release JAR inventory is not exact")

    declared: dict[str, str] = {}
    for line in sums_file.read_text().splitlines():
        match = SUM_PATTERN.fullmatch(line)
        if match is None or match.group(2) in declared:
            raise RuntimeError("release checksum manifest is invalid")
        declared[match.group(2)] = match.group(1)
    if set(declared) != expected:
        raise RuntimeError("release checksum inventory is incomplete")

    for name in sorted(expected):
        jar = directory / name
        require_regular_file(jar)
        actual = hashlib.sha256(jar.read_bytes()).hexdigest()
        if declared[name] != actual:
            raise RuntimeError(f"{name} checksum mismatch")
        _verify_executable_jar(jar)
    return declared


def _verify_executable_jar(path: Path) -> None:
    try:
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
            start_class = _manifest_value(manifest, "Start-Class")
            main_path = "BOOT-INF/classes/" + start_class.replace(".", "/") + ".class"
            bytecode = archive.read(main_path)
            shared = [name for name in names if name.startswith("BOOT-INF/lib/shared-")]
    except (KeyError, UnicodeDecodeError, zipfile.BadZipFile) as error:
        raise RuntimeError(f"{path.name} is not an executable release JAR") from error
    if len(bytecode) < 8 or bytecode[:4] != b"\xca\xfe\xba\xbe":
        raise RuntimeError(f"{path.name} application entrypoint is not bytecode")
    if int.from_bytes(bytecode[6:8], "big") != 61:
        raise RuntimeError(f"{path.name} application class is not Java 17")
    if shared != ["BOOT-INF/lib/shared-1.0.0.jar"]:
        raise RuntimeError(f"{path.name} shared protocol dependency drifted")


def _manifest_value(manifest: str, key: str) -> str:
    prefix = f"{key}: "
    values = [line[len(prefix) :] for line in manifest.splitlines() if line.startswith(prefix)]
    if len(values) != 1 or not values[0]:
        raise RuntimeError(f"manifest must contain one {key}")
    return values[0]
