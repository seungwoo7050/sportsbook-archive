from __future__ import annotations

import dataclasses
import re

from scripts.cold_gate.inventory import COMPLETED_SERVICES, LONG_RUNNING_SERVICES, SERVICES


HEX64 = re.compile(r"^[0-9a-f]{64}$")
IMAGE_ID = re.compile(r"^sha256:[0-9a-f]{64}$")


@dataclasses.dataclass(frozen=True)
class ContainerState:
    service: str
    name: str
    image_id: str
    state: str
    health: str
    exit_code: int

    @classmethod
    def parse(cls, output: str, project: str, expected_service: str) -> "ContainerState":
        fields = output.rstrip("\n").split("\t")
        if len(fields) != 8 or expected_service not in SERVICES:
            raise RuntimeError("Docker inspection receipt is invalid")
        container_id, raw_name, image_id, state, health, exit_code, label_project, service = fields
        if (
            HEX64.fullmatch(container_id) is None
            or IMAGE_ID.fullmatch(image_id) is None
            or label_project != project
            or service != expected_service
            or raw_name != f"/{project}-{expected_service}-1"
            or not exit_code.isdigit()
        ):
            raise RuntimeError("Docker inspection identity drifted")
        observed_exit = int(exit_code)
        if expected_service in LONG_RUNNING_SERVICES and (
            state != "running" or health != "healthy" or observed_exit != 0
        ):
            raise RuntimeError(f"{expected_service} is not running and healthy")
        if expected_service in COMPLETED_SERVICES and (
            state != "exited" or health != "-" or observed_exit != 0
        ):
            raise RuntimeError(f"{expected_service} did not complete successfully")
        return cls(expected_service, raw_name[1:], image_id, state, health, observed_exit)
