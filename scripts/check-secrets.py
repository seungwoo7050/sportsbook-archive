#!/usr/bin/env python3
import os
import pathlib
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
NAMES_FILE = ROOT / "config/required-secrets.txt"


def required_names() -> list[str]:
    names = [
        line.strip()
        for line in NAMES_FILE.read_text().splitlines()
        if line.strip() and not line.startswith("#")
    ]
    if len(names) != 11 or len(set(names)) != len(names):
        raise SystemExit("secret-preflight: invalid required-secret inventory")
    return names


def main() -> int:
    errors = []
    values: dict[str, str] = {}
    for name in required_names():
        value = os.environ.get(name, "")
        values[name] = value
        if not value:
            errors.append(f"{name}: missing")
        elif len(value) < 32:
            errors.append(f"{name}: shorter than 32 characters")
        elif not value.isascii() or not value.isprintable() or " " in value:
            errors.append(f"{name}: must use printable non-space ASCII")

    seen: dict[str, str] = {}
    for name, value in values.items():
        if not value:
            continue
        if value in seen:
            errors.append(f"{seen[value]} and {name}: values must differ")
        else:
            seen[value] = name

    if errors:
        for error in errors:
            print(f"secret-preflight: {error}", file=sys.stderr)
        return 1
    print("secret-preflight: validated 11 distinct keys")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
