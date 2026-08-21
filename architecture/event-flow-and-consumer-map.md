# 이벤트 흐름과 consumer map

## 동기 경로와 비동기 경로

베팅 접수는 risk와 wallet에 동기 요청을 보내 결과를 확인합니다. 동기 결과와 별도로
각 서비스는 경로에 맞는 저장과 전달 정책을 사용해 비동기 이벤트를 전파합니다.
Avro record가 존재한다고 해서 해당 명령 자체가 Kafka로 처리된다는 뜻은 아닙니다.

## Topic map

아래 표는 sportsbook 1.0에서 구현해야 하는 cross-service topology입니다.
shared-protocol 자체가 producer나 listener를 제공한다는 뜻은 아닙니다.

| topic | record | producer | key | 1.0 대상 consumer |
| --- | --- | --- | --- | --- |
| `wallet.debited.v1` | `WalletDebited` | wallet | userId | 현재 first-party consumer 없음 |
| `wallet.credited.v1` | `WalletCredited` | wallet | userId | 현재 first-party consumer 없음 |
| `wallet.debit-failed.v1` | `WalletDebitFailed` | wallet | userId | 현재 first-party consumer 없음 |
| `risk.limit.violated` | `RiskLimitViolated` | risk | userId | 현재 first-party consumer 없음 |
| `risk.pattern.suspected` | `RiskPatternSuspected` | risk | userId | 현재 first-party consumer 없음 |
| `odds.changed` | `OddsChanged` | odds-feed | eventId | gateway |
| `market.status.changed` | `MarketStatusChanged` | odds-feed | eventId | 현재 first-party consumer 없음 |
| `event.lifecycle` | `EventLifecycle` | odds-feed | eventId | settlement |
| `match.result` | `MatchResult` | odds-feed | eventId | settlement |
| `bet.placed.v1` | `BetPlacedRequested` | betting | userId | risk, settlement |
| `bet.settled.v1` | `BetSettled` | settlement | eventId | betting, gateway |
| `bet.voided.v1` | `BetVoided` | settlement | eventId | betting, gateway |
| `bet.resolution.revised.v1` | `BetResolutionRevised` | settlement | betId | betting, gateway |

Topic 이름은 service configuration과 orchestration topic manifest에서 동일하게
관리합니다.

## 전달 보장

Kafka 전달은 중복과 경로별 보장 차이를 전제로 처리합니다.

- betting, wallet, settlement의 업무 이벤트는 durable outbox를 통해 publish합니다.
- risk 알림은 best-effort이며 odds-feed critical event는 Redis Stream에서 Kafka
  publish를 확인한 뒤 ack합니다.
- consumer가 존재하는 경로는 event identity 또는 업무 idempotency key로 중복
  side effect를 막습니다.
- side effect와 offset commit 사이의 장애를 정상적인 redelivery로 처리합니다.
- exactly-once라는 가정에 의존하지 않습니다.

Topic 내부에서는 같은 partition key에 대한 순서만 기대할 수 있습니다. 서로 다른
topic 사이에는 전체 순서가 없습니다. lifecycle이 placement보다 먼저 도착하거나
revision이 최초 projection보다 먼저 도착하는 경우도 consumer 상태 전이에서
처리해야 합니다.

## 정산과 정정

`BetSettled`와 `BetVoided`는 최초 terminal projection을 만듭니다.
`BetResolutionRevised`는 SETTLED 결과 정정에만 사용합니다.

정정 producer의 순서는 다음과 같습니다.

1. corrected match result를 승인합니다.
2. 기존 payout과 새 payout의 차이를 계산합니다.
3. wallet adjustment를 idempotent operation으로 완료합니다.
4. revision과 outbox를 동일 transaction으로 저장합니다.
5. `bet.resolution.revised.v1`에 betId key로 publish합니다.

consumer는 저장된 revision number를 기준으로 다음과 같이 동작합니다.

- 더 작은 revision은 무시
- 같은 revision과 같은 payload는 no-op
- 같은 revision과 다른 payload는 conflict로 격리
- 더 큰 revision은 full snapshot으로 projection 교체
- revision이 먼저 도착한 뒤 최초 settlement가 도착하면 revision 0 이벤트를 무시

gateway가 client에 revision을 전달할 때도 revision id와 number를 포함해야 client가
역순 WebSocket message를 구분할 수 있습니다.

## Redis와 Kafka

odds-feed의 Redis projection은 betting-service의 동기 가격 조회에 사용됩니다.
Kafka odds event는 gateway push와 다른 비동기 consumer를 위한 것입니다. Redis
write 성공과 Kafka publish 성공은 같은 보장을 제공하지 않으므로, critical-event
queue와 readiness가 delivery gap을 드러내야 합니다.
