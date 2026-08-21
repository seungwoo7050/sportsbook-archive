# 빌드·실행·검증

## 도구와 artifact

- Temurin Java 17
- Maven Wrapper 3.9.11
- Redis 7
- Kafka
- Docker Compose와 k6는 HTTP release gate에만 필요

서비스 artifact는 `com.sportsbook:odds-feed-service:1.0.0`이고 실행 파일 이름은
Maven `project.build.finalName`이 결정한다. 공통 값 객체와 Avro record는
`com.sportsbook:shared-protocol:1.0.0` 하나만 사용한다.

## 격리 Maven repository 빌드

같은 Git 저장소의 `shared-protocol` 브랜치를 별도 디렉터리에 checkout한 뒤 먼저 설치한다.

```sh
export ODDS_M2=/tmp/odds-feed-m2
(cd ../shared-protocol && \
  ./mvnw -Dmaven.repo.local="${ODDS_M2}" clean install)

ADMIN_API_INTERNAL_KEY="$(openssl rand -hex 32)" \
  ./mvnw -Dmaven.repo.local="${ODDS_M2}" clean verify
```

`verify`는 compile, unit/MVC/WireMock, Embedded Kafka, Testcontainers, Spotless와 Checkstyle을
실행하고 Spring Boot executable jar를 만든다. 테스트를 생략하지 않는다.

최종 파일 이름은 버전 문자열을 script에 고정하지 않고 Maven에서 읽는다.

```sh
FINAL_NAME=$(./mvnw -q -Dstyle.color=never -DforceStdout \
  help:evaluate -Dexpression=project.build.finalName)
test -f "target/${FINAL_NAME}.jar"
```

dependency 확인에서는 shared protocol이 정확히 1.0.0 한 건이어야 한다.

```sh
./mvnw dependency:tree \
  -Dincludes=com.sportsbook:shared-protocol \
  -Dverbose
```

## 로컬 실행

mock profile도 Redis와 Kafka가 필요하다. 내부 API key는 32자 이상의 process 환경변수로만
전달한다.

```sh
export ADMIN_API_INTERNAL_KEY="$(openssl rand -hex 32)"
export REDIS_HOST=localhost
export REDIS_PORT=6379
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
```

real profile은 외부 provider key가 추가로 필요하다.

```sh
export ADMIN_API_INTERNAL_KEY="$(openssl rand -hex 32)"
export THE_ODDS_API_KEY='<provider secret>'
./mvnw spring-boot:run -Dspring-boot.run.profiles=real
```

실제 secret을 shell history, 설정 파일, test report와 애플리케이션 로그에 저장하지 않는다.

## 주요 환경 변수

| 변수 | 의미 | 기본값 또는 요구사항 |
|---|---|---|
| `ADMIN_API_INTERNAL_KEY` | admin-api 내부 인증 | 필수, 32자 이상 |
| `REDIS_HOST`, `REDIS_PORT` | projection/Stream Redis | `localhost`, `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | 네 output topic broker | `localhost:9092` |
| `SERVER_PORT` | HTTP/Actuator port | `8085` |
| `KAFKA_BROKER_ACK_TIMEOUT` | publisher ack 대기 | `5s` |
| `CRITICAL_EVENT_STREAM` | critical Redis Stream | `oddsfeed:critical-events` |
| `CRITICAL_EVENT_GROUP` | critical consumer group | `oddsfeed-publisher` |
| `CRITICAL_EVENT_CLAIM_IDLE` | pending reclaim 최소 idle | `5s` |
| `ODDSFEED_MOCK_RANDOM_SEED` | 결정적 mock seed | `424242` |
| `ODDSFEED_MOCK_TICK_INTERVAL_MS` | mock tick | `500` |
| `THE_ODDS_API_KEY` | real provider credential | real profile에서 필수 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | trace collector | local collector URL |
| `OTEL_SAMPLING_PROBABILITY` | trace sampling | `1.0` |

operator Stream의 key, group, consumer name, poll과 reclaim은 application 설정으로 조정할
수 있다. 완료 mapping은 7일 동안 유지하며 pending idempotency 상태에는 만료를 주지 않는다.

## API 점검

```sh
curl --fail http://localhost:8085/actuator/health/readiness
curl --fail 'http://localhost:8085/api/v1/events?size=20'
```

운영자 요청 예시의 key는 재시도에서도 그대로 유지한다.

```sh
curl --request POST \
  --header 'Content-Type: application/json' \
  --header 'X-Internal-Service: admin-api' \
  --header "X-Internal-Api-Key: ${ADMIN_API_INTERNAL_KEY}" \
  --header 'Idempotency-Key: admin:market:example-action' \
  --header 'X-Admin-Action-Id: 5c3ba0a8-f08a-49f4-8e5e-a1ad95ea37d3' \
  --data '{"reason":"manual risk review"}' \
  http://localhost:8085/internal/v1/events/00000000-0000-4000-8000-000000000001/markets/00000000-0000-4000-8000-000000000002/suspend
```

202는 command와 idempotency 상태가 Redis에 저장됐다는 뜻이다. Kafka 완료 여부를 응답과
동일시하지 않는다.

## 검증 층

| 검증 | 실행 | 보장 범위 |
|---|---|---|
| 전체 Maven | `./mvnw clean verify` | Java 17 compile, test, format, static analysis, jar |
| provider | JUnit/WireMock | 정규화, rate/quota, stable ID와 polling diff |
| Redis | Testcontainers | projection, registry, latch, hold, Stream reclaim |
| Kafka | Embedded Kafka | raw Avro, eventId key, ack와 초당 50건 하한 |
| MVC/security | MockMvc | public/401/403/202/409와 header 계약 |
| operator | Redis integration | atomic submit, replay/conflict, sequence와 reopen CAS |
| HTTP gate | Docker/k6 | events/odds read latency와 오류율 |

HTTP gate는 [load-test 안내](../load-test/README.md)대로 저장소 밖에 결과를 만든다.

## CI

CI는 같은 repository의 `shared-protocol` 브랜치를 checkout하고 1.0.0인지 확인한 뒤
`clean install`한다. 그 다음 실행마다 새 64자리 test key를 만들어 odds-feed의
`clean verify` process에만 전달한다.
