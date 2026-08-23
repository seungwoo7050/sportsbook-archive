from __future__ import annotations


SERVICES = (
    "postgres",
    "kafka",
    "topic-init",
    "secret-preflight",
    "redis-risk",
    "redis-odds",
    "redis-wallet",
    "redis-gateway",
    "wallet",
    "risk",
    "odds",
    "betting",
    "gateway",
    "consumer-assignment",
    "settlement",
    "admin",
    "toxiproxy",
    "prometheus",
    "loki",
    "grafana",
    "promtail",
)
COMPLETED_SERVICES = frozenset(
    {"topic-init", "secret-preflight", "consumer-assignment"}
)
LONG_RUNNING_SERVICES = frozenset(SERVICES) - COMPLETED_SERVICES
APPLICATION_SERVICES = ("wallet", "risk", "odds", "betting", "gateway", "settlement", "admin")
MIGRATION_VERSIONS = {
    "wallet": ("1", "2", "3", "4"),
    "betting": tuple(str(value) for value in range(1, 11)),
    "settlement": ("1", "3", "4", "5", "6", "7", "8", "9", "10"),
    "admin": ("1", "2"),
}
