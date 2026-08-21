# 배당 유입과 전달 경로

## 입력 계약

`OddsProvider`는 경기 검색, 경기별 event stream과 선택적 결과 조회를 제공한다. orchestrator는
`EventSummary`로 경기를 발견하고 한 event에 하나의 subscription을 유지한다.

| profile | 경기와 가격 | lifecycle/result |
|---|---|---|
| `mock` | 고정 seed의 3경기, 1X2 HOME/DRAW/AWAY | scheduled, in-play, terminal과 결정적 결과 |
| `real` | EPL/NBA, 첫 bookmaker `h2h`, stable internal ID | 제공하지 않음 |

real adapter의 process-local limiter와 Redis 월 quota는 discovery와 odds polling이 공유한다.
quota는 외부 호출 전에 소비되므로 provider 오류도 사용량에 포함된다.

## 일반 odds 경로

```text
provider OddsUpdated
  → terminal/operator/feed 상태 확인
  → 변화율 기준 판정
  → 필요한 경우 odds.changed broker ack
  → odds와 provider/effective market Redis projection
  → 최신 projection 성공 시 해당 feed hold 해제
```

`odds.changed`가 필요한 갱신은 broker ack 전에 Redis 가격을 노출하지 않는다. 기준 미만의
변경은 Kafka record 없이 현재 가격을 갱신할 수 있다. Kafka 전송 실패는 해당 마켓을
feed-availability `SUSPENDED`로 만든다. 독립 broker probe는 publisher 재시도를 허용하지만
hold 해제 근거가 아니다. 이후 공급자의 현재 odds가 broker와 Redis 경계를 모두 통과해야
hold가 사라진다.

일반 odds 경로에는 durable retry queue가 없다. provider가 같은 마켓의 최신 snapshot을
다시 제공하는 것이 회복 입력이다.

## 중요 경기·마켓 경로

market `SUSPENDED`/`CLOSED`와 terminal lifecycle은 Redis Stream에 먼저 보관된다.

```text
XADD critical envelope
  → restrictive Redis projection
  → consumer group unread/pending poll
  → raw Avro Kafka publish and ack
  → 후속 projection
  → XACK
  → best-effort XDEL
```

consumer가 종료되면 idle 시간이 지난 pending record를 다른 poll이 reclaim한다. Kafka ack 뒤
XACK 전에 종료된 record는 다시 전달되므로 Kafka 출력은 at-least-once다. terminal lifecycle은
`event:markets:{eventId}` registry를 읽어 모든 알려진 마켓을 먼저 닫는다. registry와
terminal latch가 Redis에 있으므로 새 process도 같은 폐쇄 집합을 복원한다.

`OPEN`은 Kafka ack가 완료된 뒤에만 유효 projection으로 승격한다. 다음 Redis 우선순위는
모든 provider와 operator write에 공통이다.

```text
event terminal 또는 market terminal
  > operator CLOSED/SUSPENDED
  > feed hold
  > provider OPEN/SUSPENDED
```

## Kafka 출력

모든 record는 Schema Registry framing 없이 shared protocol의 raw Avro binary를 사용하고,
Kafka key는 `eventId` 문자열이다.

| topic | Avro record | 주요 consumer |
|---|---|---|
| `odds.changed` | `OddsChanged` | gateway fan-out |
| `market.status.changed` | `MarketStatusChanged` | market 상태 구독자 |
| `event.lifecycle` | `EventLifecycle` | settlement |
| `match.result` | `MatchResult` | settlement |

같은 key는 한 topic 안에서만 순서를 제공한다. 서로 다른 topic 사이에는 전역 순서가 없다.
따라서 settlement는 terminal lifecycle을 내구 상태로 남기고, 그 뒤 늦게 도착한 placement도
다시 판정해야 한다.

## Redis projection

| key | 의미 | 만료 |
|---|---|---|
| `odds:{event}:{market}:{selection}` | 현재 decimal odds | cache TTL |
| `event:{event}` | 현재 경기 요약 | cache TTL |
| `market:{event}:{market}` | betting이 읽는 유효 상태 | cache TTL |
| `market:provider:{event}:{market}` | 마지막 provider 상태 | cache TTL |
| `market:override:{event}:{market}` | operator restrictive 상태 | 명시적 reopen까지 |
| `event:markets:{event}` | terminal closure용 market registry | active cache 수명과 갱신 |
| `event:terminal:{event}` | terminal event latch | 없음 |
| `market:terminal:{event}:{market}` | provider-CLOSED latch | 없음 |
| `market:feed-hold:{event}:{market}` | broker 장애 후 가격 차단 | 최신 projection 또는 cache TTL까지 |
| `oddsfeed:critical-events` | 중요 event envelope Stream | ack 뒤 cleanup |

terminal latch는 provider의 late `OPEN`과 operator reopen을 거절한다. feed recovery는
operator override와 terminal latch를 변경하지 않는다.

## 전달 보장의 한계

- critical Stream은 unread/pending 회수와 ack를 제공하지만 poison 격리와 단계 checkpoint는 없다.
- critical cleanup은 XACK 뒤 XDEL을 best-effort로 수행한다.
- raw Avro consumer는 duplicate를 idempotently 처리해야 한다.
- single process 안에서의 event subscription만 조정하며 여러 replica의 provider 생산을 조정하지 않는다.
- ordinary odds의 Kafka와 여러 Redis key는 하나의 cross-system transaction이 아니다.
