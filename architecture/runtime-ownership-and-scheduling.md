# 런타임 소유권과 스케줄링

## process 내부 소유권

| 구성요소 | 소유 자원 | 종료 시 동작 |
|---|---|---|
| Spring MVC/Tomcat | public GET, internal POST, Actuator | graceful HTTP shutdown |
| `FeedOrchestrator` | event discovery와 provider subscription | 등록 subscription dispose |
| mock provider | 경기 상태, random walk, result | process와 함께 종료 |
| real provider | WebClient, limiter, quota 호출 | 진행 중 HTTP의 별도 drain 없음 |
| critical processor | Redis Stream consumer identity와 poll | ack 전 record는 pending 유지 |
| operator processor | command Stream poll과 market sequence | ack 전 record는 pending 유지 |
| Kafka producer | 네 raw Avro topic 전송 | Spring lifecycle에서 close |
| Lettuce | projection, registry, latch, Stream 연결 | Spring lifecycle에서 close |

## 스케줄링

provider refresh, mock tick, scenario rotation, real poll, critical drain, operator drain과 broker
probe는 Spring scheduled task다. 별도 task pool을 구성하지 않은 실행에서는 한 scheduler
thread를 공유한다. WebClient 대기, Redis 호출 또는 Kafka ack 대기가 길어지면 다른 cadence도
늦어질 수 있다.

provider subscription은 Reactor worker에서 재시도 backoff를 수행한다. retry는 process-local이며
여러 replica 사이에서 event ownership을 조정하지 않는다. Redis consumer group은 이미
enqueue된 Stream record를 나눌 수 있지만 provider poll과 enqueue 중복을 막는 leader lease는
아니다.

## 시작 순서

1. 내부 API key와 typed 설정을 검증한다.
2. Redis/Kafka client와 provider를 만든다.
3. mock provider는 고정 seed 경기 집합을 만들고 real provider는 credential을 검증한다.
4. orchestrator가 경기를 발견하고 Redis event/market registry를 hydrate한다.
5. event subscription과 critical/operator poll이 시작된다.
6. broker probe와 delivery health가 readiness에 반영된다.

Redis가 유지된 process 교체에서는 terminal latch와 market registry가 source of truth다.
JVM map은 조회 가속과 active subscription 소유권일 뿐 terminal 안전성의 유일한 저장소가
아니다.

## Health와 meter

- liveness는 process가 요청을 처리할 수 있는지 나타낸다.
- readiness는 Spring readiness state, Redis, Kafka publisher와 critical/operator delivery를 묶는다.
- Prometheus는 Spring 기본 meter와 다음 application meter를 노출한다.

```text
oddsfeed.critical.delivery.enqueued
oddsfeed.critical.delivery.acknowledged
oddsfeed.critical.delivery.reclaimed
oddsfeed.critical.delivery.failure
oddsfeed.critical.delivery.pending
oddsfeed.operator.action.processed
oddsfeed.operator.action.processing.failure
oddsfeed.operator.action.pending
```

readiness는 전체 unread Stream 길이, poison record, provider freshness, scheduler 지연,
catalog drift 또는 downstream consumer lag를 모두 증명하지 않는다. broker probe 성공도
기존 feed hold를 즉시 해제하지 않는다.

## 배포 전제와 종료

현재 안전한 provider 생산 모델은 단일 active instance다. 여러 instance를 사용하려면
discovery/poll/subscription에 leader lease 또는 event partition ownership이 필요하다.
consumer group만 추가해 이 조건을 충족할 수 없다.

HTTP graceful shutdown과 Spring bean close는 제공한다. 그러나 provider의 진행 중 WebClient,
scheduled callback, Kafka late future와 모든 Stream backlog가 종료 제한 시간 안에 drain되는
것은 보장하지 않는다. ack 전 critical/operator record는 Redis pending state에서 다음
process가 reclaim한다.

## 외부 의존성

- Redis 7: projection, terminal/override/feed state, quota와 두 Stream
- Kafka: `odds.changed`, `market.status.changed`, `event.lifecycle`, `match.result`
- The Odds API: `real` profile에서만 사용
- OpenTelemetry endpoint: tracing exporter이며 기능 성공의 필수 의존성은 아님

`ADMIN_API_INTERNAL_KEY`와 `THE_ODDS_API_KEY`는 deployment secret으로만 주입한다. 서비스
설정 파일에는 실제 값을 두지 않는다.
