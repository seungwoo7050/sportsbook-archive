# odds-feed-service

`odds-feed-service`는 외부 배당 공급자를 내부 경기·마켓·배당 계약으로 정규화하는
Java 17 서비스다. 현재 조회 상태는 Redis에 투영하고, 변경 알림과 정산 입력은 raw Avro
Kafka record로 발행한다. 베팅 접수, 지갑 처리, 위험 판정과 정산은 소유하지 않는다.

Maven 좌표는 `com.sportsbook:odds-feed-service:1.0.0`이며
`com.sportsbook:shared-protocol:1.0.0`을 사용한다.

## 책임 경계

```text
mock provider ─┐
               ├─ discovery / subscription ─ odds ─ Kafka ack ─ Redis projection
real provider ─┘                         └ critical ─ Redis Stream ─ fail-close ─ Kafka

admin-api ─ authenticated command ─ operator Stream ─ async Kafka ─ ordered completion

gateway / betting ─ public GET and Redis read model
settlement       ─ lifecycle and result topics
```

- `mock` profile은 결정적인 경기, 1X2 마켓, 배당 walk, lifecycle과 결과를 제공한다.
- `real` profile은 The Odds API의 EPL/NBA 첫 bookmaker `h2h` 가격을 정규화한다.
- 네 Kafka topic의 payload와 key 계약은 [전달 경로](architecture/odds-ingress-and-delivery-paths.md)에 정리한다.
- 운영자 suspend/close/reopen은 동기 Kafka API가 아니라 내구 command 접수 API다.

## 상태 안전성

Redis 유효 마켓 상태는 다음 우선순위를 따른다.

```text
event terminal 또는 provider terminal CLOSED
  > operator CLOSED/SUSPENDED
  > feed-availability SUSPENDED
  > provider OPEN/SUSPENDED
```

terminal latch와 provider-CLOSED latch에는 TTL이 없다. 활성 경기의 마켓 registry는
Redis에 유지하므로 process가 교체되어도 terminal lifecycle이 기존 마켓 전체를 닫는다.
restrictive 상태는 내구 enqueue 뒤 Redis에 먼저 반영되고 Kafka에 발행된다. `OPEN`은
broker ack 이후에만 반영된다.

Kafka 장애가 발생한 마켓에는 feed hold가 생긴다. broker probe 성공만으로 hold를
해제하지 않으며, 공급자의 최신 odds가 성공적으로 투영된 뒤에만 해제한다. 이 과정은
terminal latch나 operator override를 지우지 않는다.

## HTTP API

| method | path | 접근 | 결과 |
|---|---|---|---|
| `GET` | `/api/v1/events?cursor=&size=` | anonymous | cursor 경기 목록 |
| `GET` | `/api/v1/events/{eventId}` | anonymous | 경기 상세 또는 404 |
| `GET` | `/api/v1/odds/{eventId}/{marketId}/{selectionId}` | anonymous | 현재 배당 또는 404 |
| `POST` | `/internal/v1/events/{eventId}/markets/{marketId}/suspend` | `admin-api` | 내구 접수 202 |
| `POST` | `/internal/v1/events/{eventId}/markets/{marketId}/close` | `admin-api` | 내구 접수 202 |
| `POST` | `/internal/v1/events/{eventId}/markets/{marketId}/reopen` | `admin-api` | 내구 접수 202 |

내부 요청은 `X-Internal-Service: admin-api`, `X-Internal-Api-Key`, 안정적인
`Idempotency-Key`, UUID `X-Admin-Action-Id`와 trim된 1~256자 `reason`을 요구한다.
credential 누락·불일치는 401, 올바른 key를 가진 비허용 caller는 403이다. 동일
idempotency key와 동일 요청은 같은 202를 replay하고 payload가 다르면 409를 반환한다.
terminal 마켓 reopen도 409다.

health와 Prometheus는 anonymous다. health detail은 인증되지 않은 요청에 노출하지 않으며,
그 밖의 management 및 미등록 경로는 허용하지 않는다. 자세한 접수·순서·재시도 경계는
[운영자 마켓 안전성](architecture/operator-market-safety.md)을 따른다.

## 빌드와 실행

Java 17과 Maven Wrapper를 사용한다. 먼저 같은 저장소의 `shared-protocol` 브랜치가 만든
1.0.0 artifact를 Maven repository에 설치한다.

```sh
export ODDS_M2=/tmp/odds-feed-m2
(cd ../shared-protocol && ./mvnw -Dmaven.repo.local="${ODDS_M2}" clean install)
ADMIN_API_INTERNAL_KEY="$(openssl rand -hex 32)" \
  ./mvnw -Dmaven.repo.local="${ODDS_M2}" clean verify
```

로컬 mock 실행에는 Redis와 Kafka가 필요하다.

```sh
ADMIN_API_INTERNAL_KEY="$(openssl rand -hex 32)" \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=mock
```

`real` profile은 `THE_ODDS_API_KEY`를 추가로 요구한다. 내부 API key는 환경변수로만
주입하며 설정 파일과 로그에 기록하지 않는다. 전체 설정과 검증 명령은
[빌드·실행·검증](docs/build-run-and-verify.md)에 있다.

## 관측과 검증

- `/actuator/health/liveness`: process 생존성
- `/actuator/health/readiness`: application, Redis, Kafka publisher, critical/operator delivery 상태
- `/actuator/prometheus`: Spring 기본 meter와 critical/operator Stream 처리 meter
- `KafkaPublishThroughputTest`: broker acknowledgement 기준 초당 50건 하한
- `load-test/run-http-gate.sh`: event/odds 각각 60초 예열과 60초 측정 5회

HTTP gate 결과는 저장소 외부 release artifact로만 생성한다. 실행 방법과 합격 기준은
[부하 검증](load-test/README.md)에 있다.

## 현재 지원 제한

- Redis Stream poison record 격리와 단계별 delivery checkpoint는 제공하지 않는다.
- 여러 instance의 provider 생산을 조정하는 leader lease는 제공하지 않는다.
- provider poll, subscription retry, critical/operator drain은 전용 scheduler로 분리되지 않는다.
- readiness는 전체 unread backlog, provider freshness와 scheduler lag를 완전히 증명하지 않는다.
- real provider는 가격 polling만 지원하며 lifecycle과 result를 생성하지 않는다.
- mock 경기 집합은 process 시작 시 생성되며 장기 실행 중 새 세대를 보충하지 않는다.
- 종료 시 HTTP graceful shutdown은 사용하지만 모든 provider/Kafka/Stream 작업의 drain을 보장하지 않는다.
- lifecycle과 placement가 서로 다른 topic에서 경쟁하는 문제는 settlement의 terminal tombstone과
  late-placement catch-up이 함께 해결해야 한다.

이 제한은 [런타임 소유권과 스케줄링](architecture/runtime-ownership-and-scheduling.md)에 운영
영향과 함께 정리한다.

## 문서

- [provider·Redis·Kafka 전달 경로](architecture/odds-ingress-and-delivery-paths.md)
- [운영자 마켓 안전성](architecture/operator-market-safety.md)
- [런타임 소유권과 스케줄링](architecture/runtime-ownership-and-scheduling.md)
- [빌드·실행·검증](docs/build-run-and-verify.md)
- [HTTP 부하 검증](load-test/README.md)
