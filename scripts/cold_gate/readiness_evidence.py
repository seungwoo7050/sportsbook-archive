from __future__ import annotations

import decimal
import re
from collections.abc import Callable

from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.container_http import ContainerHttpClient
from scripts.cold_gate.evidence import EvidenceStore
from scripts.cold_gate.polling import poll_until


READINESS_ENDPOINTS = (
    ("wallet", "/actuator/health"),
    ("risk", "/actuator/health/readiness"),
    ("odds", "/actuator/health/readiness"),
    ("betting", "/actuator/health"),
    ("gateway", "/actuator/health/readiness"),
    ("settlement", "/actuator/health/readiness"),
    ("admin", "/actuator/health/readiness"),
)
INTEGRITY_METRICS = (
    "wallet_integrity_total_drift",
    "wallet_integrity_scan_failed",
    "wallet_integrity_last_checked_epoch_seconds",
)
NUMBER = r"[-+]?(?:[0-9]+(?:\.[0-9]+)?|\.[0-9]+)(?:[eE][-+]?[0-9]+)?"
ClientFactory = Callable[[ComposeProject, str], ContainerHttpClient]


class ReadinessEvidence:
    def __init__(
        self,
        compose: ComposeProject,
        store: EvidenceStore,
        client_factory: ClientFactory = ContainerHttpClient,
    ) -> None:
        if compose.context is not store.context:
            raise RuntimeError("readiness evidence ownership mismatch")
        self.compose = compose
        self.store = store
        self.client_factory = client_factory

    def capture(self) -> None:
        rows = ["kind\tservice\tcheck\tvalue"]
        for service, endpoint in READINESS_ENDPOINTS:
            client = self.client_factory(self.compose, service)
            response = client.request("GET", endpoint).require_status(200)
            payload = response.json()
            if not isinstance(payload, dict) or payload.get("status") != "UP":
                raise RuntimeError(f"{service} readiness is not exactly UP")
            rows.append(f"readiness\t{service}\t{endpoint}\tUP")

        wallet = self.client_factory(self.compose, "wallet")
        metrics = poll_until(
            "Wallet integrity first scan",
            lambda: self._metrics(wallet),
            self._clean_first_scan,
            timeout=60,
            interval=0.25,
        )
        rows.extend(
            (
                f"integrity\twallet\t{INTEGRITY_METRICS[0]}\t0",
                f"integrity\twallet\t{INTEGRITY_METRICS[1]}\t0",
                f"integrity\twallet\t{INTEGRITY_METRICS[2]}\t{int(metrics[2])}",
            )
        )
        self.store.write("readiness.tsv", "\n".join(rows) + "\n")

    @staticmethod
    def _metrics(client: ContainerHttpClient) -> tuple[decimal.Decimal, ...]:
        response = client.request("GET", "/actuator/prometheus").require_status(200)
        try:
            body = response.body.decode("utf-8")
        except UnicodeDecodeError as error:
            raise RuntimeError("Wallet metrics are not UTF-8") from error
        values = []
        for name in INTEGRITY_METRICS:
            matches = re.findall(rf"^{name}\s+({NUMBER})$", body, re.MULTILINE)
            if len(matches) != 1:
                raise RuntimeError(f"expected one unlabelled {name} metric")
            values.append(decimal.Decimal(matches[0]))
        return tuple(values)

    @staticmethod
    def _clean_first_scan(values: tuple[decimal.Decimal, ...]) -> bool:
        drift, failed, checked = values
        if drift != 0 or failed != 0:
            raise RuntimeError("Wallet integrity first scan is not clean")
        if checked != checked.to_integral_value():
            raise RuntimeError("Wallet integrity timestamp is not an epoch second")
        return checked > 0
