#!/usr/bin/env python3
import os
import pathlib
import sys
import zipfile


ARTIFACTS = {
    "wallet": "wallet-service-1.0.0.jar",
    "risk": "risk-service-1.0.0.jar",
    "odds": "odds-feed-service-1.0.0.jar",
    "betting": "betting-service-1.0.0.jar",
    "gateway": "gateway-1.0.0.jar",
    "settlement": "settlement-service-1.0.0.jar",
    "admin": "admin-api-1.0.0.jar",
}


logical = pathlib.Path.cwd().name
if os.environ.get("FAIL_LOGICAL") == logical:
    raise SystemExit(9)
repository = next(
    pathlib.Path(value.split("=", 1)[1])
    for value in sys.argv
    if value.startswith("-Dmaven.repo.local=")
)
if not repository.is_dir():
    raise SystemExit("isolated Maven repository is missing")
artifact = pathlib.Path("target") / ARTIFACTS[logical]
artifact.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(artifact, "w") as archive:
    archive.writestr("BOOT-INF/classes/Probe.class", logical.encode())
