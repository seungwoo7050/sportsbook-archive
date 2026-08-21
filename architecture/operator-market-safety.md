# 운영자 마켓 안전성

## 요청 계약

```http
POST /internal/v1/events/{eventId}/markets/{marketId}/{suspend|close|reopen}
X-Internal-Service: admin-api
X-Internal-Api-Key: <환경변수로 주입한 32자 이상 secret>
Idempotency-Key: <안정적인 요청 key>
X-Admin-Action-Id: <UUID>
Content-Type: application/json

{"reason":"trim 후 1~256자"}
```

`ADMIN_API_INTERNAL_KEY`가 없거나 32자 미만이면 애플리케이션 시작이 실패한다. filter는
caller 이름만 신뢰하지 않고 API key digest를 constant-time으로 비교한다. credential이
없거나 틀리면 401, 올바른 key를 가진 caller가 `admin-api`가 아니면 403이다. secret과 인증
header는 로그에 남기지 않는다.

public event/odds GET, health와 Prometheus만 anonymous다. 다른 route는 기본 거절한다.

## 202의 의미

202는 Kafka publish 완료가 아니다. 다음 두 상태가 하나의 Redis Lua 경계에서 저장됐다는
뜻이다.

1. `oddsfeed:operator:*` idempotency 상태
2. 처리할 command의 Redis Stream record

close와 suspend는 같은 원자 경계에서 operator override와 유효 restrictive 상태도
fail-close한다. reopen은 command를 넣되 기존 override를 유지한다. 따라서 broker 장애나
process 교체가 접수된 restrictive 상태를 되돌리지 않는다.

## Idempotency와 fingerprint

fingerprint는 다음 canonical UTF-8 값을 SHA-256으로 계산한다.

```text
format version
authenticated caller
action
lowercase event UUID
lowercase market UUID
requested status
normalized reason
```

같은 idempotency key와 같은 fingerprint는 새 Stream record 없이 기존 202를 반환한다.
같은 key의 fingerprint가 다르면 409다. action ID는 감사 상관관계이며 idempotency key를
대신하지 않는다.

완료 상태는 7일 뒤 만료한다. pending 상태는 처리되기 전에는 만료하지 않는다.

## 마켓별 순서

각 command는 마켓별 증가 sequence를 가진다. processor는 predecessor가 완료된 command만
적용하므로 동시에 접수된 close/reopen도 Redis 접수 순서를 보존한다.

```text
submit → Stream pending → predecessor 확인 → Kafka ack → completion CAS → XACK+XDEL
```

- close/suspend: 접수 시 이미 restrictive projection이며 ack 뒤 command를 완료한다.
- reopen: terminal latch가 있으면 접수 단계에서 409다.
- reopen: Kafka ack 뒤 predecessor와 현재 override가 예상값일 때만 CAS로 override를 지운다.
- 뒤따르는 restrictive command가 있으면 오래된 reopen이 새 restrictive 상태를 지우지 못한다.
- 완료된 operator record는 하나의 Lua 경계에서 XACK와 XDEL을 수행한다.

Kafka ack 후 Redis completion 전에 process가 종료되면 같은 command가 Kafka에 다시
발행될 수 있다. 이는 의도한 at-least-once 경계이며 consumer가 event duplicate를 처리해야
한다.

## 호출자 책임

admin-api는 사용자 요청의 재시도 전반에 같은 `Idempotency-Key`와
`X-Admin-Action-Id`를 유지한다. 202를 market OPEN 완료로 해석하지 않고 접수 완료로
해석한다. 401은 credential 설정 문제, 403은 caller 권한 문제, 409는 key 충돌 또는
terminal 불변식 위반으로 다룬다.

orchestration은 odds-feed와 admin-api에 같은 `ADMIN_API_INTERNAL_KEY` 값을 secret으로
주입한다. 실제 값은 Compose 파일, 로그와 문서에 저장하지 않는다.
