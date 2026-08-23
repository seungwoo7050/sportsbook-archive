from __future__ import annotations

import time

from e2e.base_oracles import BaseOracles
from e2e.bet_api import BetApi
from e2e.correction_oracles import CorrectionOracles
from e2e.model import ScenarioIds
from e2e.placement_oracles import PlacementOracles
from e2e.settlement_admin_api import SettlementAdminApi
from e2e.wallet_api import WalletApi
from scripts.cold_gate.build import ReleaseArtifacts
from scripts.cold_gate.chaos import ChaosClient
from scripts.cold_gate.compose import ComposeProject
from scripts.cold_gate.container_http import ContainerHttpClient
from scripts.cold_gate.context import ColdGateContext
from scripts.cold_gate.database import PostgresClient
from scripts.cold_gate.fixtures import FixturePublisher
from scripts.cold_gate.http import HostHttpClient
from scripts.cold_gate.jwt import JwtSigner
from scripts.cold_gate.kafka import KafkaAdmin
from scripts.cold_gate.polling import poll_until
from scripts.cold_gate.redis import RedisClient
from scripts.cold_gate.secrets import RuntimeSecrets
from scripts.cold_gate.stack import ColdStack


SETTLEMENT_ASSIGNMENTS = frozenset(
    (topic, partition)
    for topic in ("bet.placed.v1", "event.lifecycle", "match.result")
    for partition in range(3)
)


class E2eRuntime:
    def __init__(
        self,
        context: ColdGateContext,
        compose: ComposeProject,
        artifacts: ReleaseArtifacts,
        secrets: RuntimeSecrets,
    ) -> None:
        if compose.context is not context:
            raise RuntimeError("E2E runtime ownership mismatch")
        gateway_port = ColdStack(context, compose).loopback_port("gateway", 8080)
        database = PostgresClient(compose)
        self.context = context
        self.compose = compose
        self.environment = secrets.environment
        self.signer = JwtSigner(context, secrets.private_key)
        self.bets = BetApi(HostHttpClient(f"http://127.0.0.1:{gateway_port}"))
        self.wallet_api = WalletApi(
            ContainerHttpClient(compose, "wallet"),
            secrets.environment["WALLET_PLATFORM_API_KEY"],
        )
        self.settlement_admin = SettlementAdminApi(ContainerHttpClient(compose, "admin"))
        self.settlement_http = ContainerHttpClient(compose, "settlement")
        self.base = BaseOracles(database)
        self.placements = PlacementOracles(database)
        self.corrections = CorrectionOracles(database)
        self.odds = RedisClient(compose, "redis-odds")
        self.risk = RedisClient(compose, "redis-risk")
        self.kafka = KafkaAdmin(compose)
        self.fixtures = FixturePublisher(context, compose, artifacts.fixture_jar)
        self.chaos = ChaosClient(compose)

    def user_token(self, fixture: ScenarioIds) -> str:
        return self.signer.user(fixture.user, int(time.time()))

    def admin_token(self) -> str:
        return self.signer.admin(int(time.time()))

    def seed(self, fixture: ScenarioIds) -> None:
        self.wallet_api.open_and_fund(fixture)
        if self.odds.scalar("SET", f"market:{fixture.event}:{fixture.market}", "OPEN", "EX", "3600") != "OK":
            raise RuntimeError("market fixture was not seeded")
        if self.odds.scalar(
            "SET", f"odds:{fixture.event}:{fixture.market}:{fixture.selection}", "2.0000", "EX", "3600"
        ) != "OK":
            raise RuntimeError("odds fixture was not seeded")

    def wait_for_settlement_assignments(self) -> None:
        def complete(actual: frozenset[tuple[str, int]]) -> bool:
            unexpected = actual - SETTLEMENT_ASSIGNMENTS
            if unexpected:
                raise RuntimeError(f"Settlement owns unexpected Kafka assignments: {unexpected}")
            return actual == SETTLEMENT_ASSIGNMENTS

        poll_until(
            "Settlement Kafka assignments",
            lambda: self.kafka.assignments("settlement-service"),
            complete,
            timeout=90,
            interval=1,
        )

    def stop_settlement(self) -> None:
        self.compose.run("stop", "--timeout", "30", "settlement", capture_output=True)

    def start_settlement(self) -> None:
        self.compose.run("start", "settlement", capture_output=True)
        response = poll_until(
            "Settlement restart readiness",
            lambda: self.settlement_http.request("GET", "/actuator/health/readiness"),
            lambda value: value.status == 200,
            timeout=60,
            interval=0.5,
        )
        payload = response.json()
        if not isinstance(payload, dict) or payload.get("status") != "UP":
            raise RuntimeError("Settlement restart readiness payload drifted")
