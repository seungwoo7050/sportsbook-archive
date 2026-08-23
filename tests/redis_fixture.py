import json

from tests.compose_fixture import ComposeFixture


class RedisFixture(ComposeFixture):
    def start_redis(self, *services: str) -> None:
        started = self.compose("up", "--detach", "--wait", *services)
        self.assertEqual(started.returncode, 0, started.stderr)

    def redis(self, service: str, *arguments: str):
        return self.compose("exec", "--no-TTY", service, "redis-cli", *arguments)

    def assert_redis_contract(self, service: str, volume: str) -> None:
        self.start_redis(service)
        for option, expected in {
            "appendonly": "yes",
            "appendfsync": "everysec",
            "maxmemory-policy": "noeviction",
        }.items():
            with self.subTest(service=service, option=option):
                config = self.redis(service, "CONFIG", "GET", option)
                self.assertEqual(config.returncode, 0, config.stderr)
                self.assertEqual(config.stdout.splitlines(), [option, expected])

        rendered = self.compose("config", "--format", "json")
        self.assertEqual(rendered.returncode, 0, rendered.stderr)
        service_config = json.loads(rendered.stdout)["services"][service]
        data_mounts = [
            mount
            for mount in service_config["volumes"]
            if mount["target"] == "/data"
        ]
        self.assertEqual(
            data_mounts,
            [
                {
                    "type": "volume",
                    "source": volume,
                    "target": "/data",
                    "volume": {},
                }
            ],
        )

    def assert_isolated_values(self, *services: str) -> None:
        self.start_redis(*services)
        for index, service in enumerate(services):
            stored = self.redis(service, "SET", "contract:isolation", str(index))
            self.assertEqual(stored.returncode, 0, stored.stderr)
        for index, service in enumerate(services):
            loaded = self.redis(service, "GET", "contract:isolation")
            self.assertEqual(loaded.returncode, 0, loaded.stderr)
            self.assertEqual(loaded.stdout.strip(), str(index))
