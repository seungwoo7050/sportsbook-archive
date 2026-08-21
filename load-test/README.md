# HTTP release gate

이 gate는 public 경기 목록과 단일 배당 조회를 독립된 Redis/Kafka 상태에서 검사한다.
Kafka publisher의 초당 50건 검사는 Maven의 `KafkaPublishThroughputTest`가 별도로 담당한다.

## 요구 도구

- Java 17
- Docker Compose
- k6
- curl과 jq
- OpenSSL
- local Maven repository의 `shared-protocol:1.0.0`

## 실행

`RESULT_ROOT`는 존재하지 않는 절대 경로이며 Git 저장소 밖이어야 한다. script는 결과를
tracked tree에 만들지 않는다.

```sh
RESULT_ROOT=/tmp/odds-http-gate-result \
  ./load-test/run-http-gate.sh
```

선택 환경 변수:

| 변수 | 기본값 | 의미 |
|---|---|---|
| `SERVER_PORT` | `8085` | service HTTP port |
| `REDIS_PORT` | `6392` | Docker Redis host port |
| `KAFKA_PORT` | `9096` | Docker Kafka host port |
| `REQUEST_RATE` | `1000` | endpoint별 초당 도착 요청 수 |
| `PREALLOCATED_VUS` | `200` | k6 pre-allocated VU |
| `MAX_VUS` | `500` | k6 VU 상한 |
| `COMPOSE_PROJECT_NAME` | `odds-feed-http-gate` | Compose 격리 이름 |
| `MAVEN_REPO_LOCAL` | Maven 기본값 | shared 1.0.0이 설치된 repository |

script는 `clean verify`로 jar를 만들고 Maven `project.build.finalName`에서 실행 파일 이름을
읽는다. event와 odds를 각각 fresh Redis/Kafka에서 검사하며, 고정 seed와 긴 tick interval로
한 process의 read model을 측정 동안 유지한다. 내부 API key는 build와 service 시작마다 새로
만들며 결과와 로그에 출력하지 않는다.

## 측정 절차

각 endpoint는 다음 순서로 단독 검사한다.

1. 60초 warm-up
2. 60초 measurement 5회
3. 각 measurement의 k6 threshold 판정

measurement 하나라도 다음 조건을 만족하지 못하면 script가 실패한다.

- `http_req_duration` p99 < 50ms
- `http_req_failed` < 0.1%
- checks > 99.9%
- dropped iterations = 0

warm-up은 동일 request rate를 사용하지만 threshold를 release 판정에 넣지 않는다.

## 외부 결과

결과 디렉터리는 endpoint별 k6 summary와 service log만 담는다.

```text
RESULT_ROOT/
  events-service.log
  events/warmup.json
  events/measure-1.json ... measure-5.json
  odds-service.log
  odds/warmup.json
  odds/measure-1.json ... measure-5.json
```

이 gate는 고정 mock read model의 HTTP 성능을 검사한다. real provider, multi-instance,
critical/operator Stream end-to-end 처리량, downstream consumer와 운영 cluster 용량을
증명하지 않는다. 결과 보존과 폐기는 release 환경이 담당한다.
