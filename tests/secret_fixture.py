import os
import pathlib
import subprocess
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/check-secrets.py"
NAMES = tuple((ROOT / "config/required-secrets.txt").read_text().splitlines())
VALID = {
    name: f"contract-key-{index:02d}-" + (chr(65 + index) * 32)
    for index, name in enumerate(NAMES)
}


def run_preflight(values: dict[str, str]) -> subprocess.CompletedProcess[str]:
    environment = {"PATH": os.environ.get("PATH", "")} | values
    return subprocess.run(
        [sys.executable, str(SCRIPT)],
        cwd=ROOT,
        env=environment,
        text=True,
        capture_output=True,
        check=False,
    )


def assert_values_redacted(test, result: subprocess.CompletedProcess[str]) -> None:
    output = result.stdout + result.stderr
    for value in VALID.values():
        test.assertNotIn(value, output)
