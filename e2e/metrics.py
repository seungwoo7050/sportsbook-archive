from __future__ import annotations

import decimal
import re

from scripts.cold_gate.container_http import ContainerHttpClient


METRIC_NAME = re.compile(r"^[a-z][a-z0-9_]+$")
NUMBER = r"[-+]?(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)(?:[eE][-+]?[0-9]+)?"


def metric_value(client: ContainerHttpClient, name: str) -> decimal.Decimal:
    if not METRIC_NAME.fullmatch(name):
        raise ValueError("Prometheus metric name is invalid")
    response = client.request("GET", "/actuator/prometheus").require_status(200)
    try:
        text = response.body.decode("utf-8")
    except UnicodeDecodeError as error:
        raise RuntimeError("Prometheus response is not UTF-8") from error
    pattern = re.compile(
        rf"^{re.escape(name)}(?:\{{[^}}\r\n]*\}})?\s+({NUMBER})$",
        re.MULTILINE,
    )
    values = pattern.findall(text)
    if not values:
        return decimal.Decimal(0)
    if len(values) != 1:
        raise RuntimeError(f"expected one {name} metric")
    try:
        return decimal.Decimal(values[0])
    except decimal.InvalidOperation as error:
        raise RuntimeError(f"{name} metric is not numeric") from error
