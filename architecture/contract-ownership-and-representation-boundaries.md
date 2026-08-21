# 계약 소유권과 표현 경계

## 세 가지 표현

동일한 업무 개념이라도 Java domain, JSON API, Avro event는 서로 다른 경계를
가집니다.

| 표현 | 목적 | 주요 제약 |
| --- | --- | --- |
| Java | 서비스 내부 타입 안전성 | 생성자에서 null과 구조적 불변식 검증 |
| JSON | 동기 HTTP API | Jackson annotation과 record component가 wire shape 결정 |
| Avro | Kafka 비동기 전달 | schema의 name, namespace, field order와 type이 계약 |

표현을 서로 자동 치환 가능한 것으로 취급하지 않습니다. 각 adapter가 명시적으로
변환하고, 경계별 테스트가 변환 결과를 검증합니다.

## 금액

`com.sportsbook.protocol.value.Money`는 Java 계산용 값 객체입니다.

- long minor unit을 사용합니다.
- currency가 다른 금액의 연산과 비교를 거부합니다.
- add, subtract, multiply, negate에서 overflow를 조용히 허용하지 않습니다.
- ledger entry 표현을 위해 음수 금액을 허용합니다.

`com.sportsbook.protocol.event.Money`는 Avro record입니다. 생성된 클래스이므로
Java 값 객체의 메서드나 검증을 갖지 않습니다. producer와 consumer adapter가
currency와 업무별 금액 범위를 검증합니다.

## 식별자와 idempotency

Java API는 UUID를 그대로 주고받는 대신 typed ID를 사용합니다. 동일한 UUID라도
`EventId`와 `MarketId`, `BetId`와 `UserId`는 서로 대입할 수 없습니다.
JSON에서는 각 typed ID가 canonical UUID string으로 직렬화됩니다.

`IdempotencyKey`는 다음 wire 조건만 보장합니다.

- non-blank
- 최대 128자
- printable ASCII

요청 결과 저장, payload fingerprint 비교, 중복 side effect 방지는 각 서비스가
durable store와 unique constraint로 구현합니다.

## 베팅 조합

`BetSlip`은 모든 consumer가 안전하게 해석할 수 있는 구조만 허용합니다.

- SINGLE은 selection 1개
- MULTIPLE은 selection 2개 이상
- SYSTEM은 `totalSelections`와 실제 selection 수가 동일
- stake는 양수
- SETTLED 상태는 result와 settled time을 포함
- WON, PUSH, VOID는 payout을 포함
- LOST는 payout을 포함하지 않음
- 입력 selection list는 defensive copy

same-event 정책, market 조합 제한, 최대 selection 수, 배당 drift 허용치는
betting-service가 소유합니다.

## 오류

`ErrorCode`는 서비스 경계를 넘어 의미가 동일해야 하는 오류만 포함합니다.
서비스 내부 전용 오류는 해당 서비스에 둡니다.

`ProblemDetail`은 Spring Web 타입에 의존하지 않는 JSON record입니다. HTTP
adapter는 이를 status와 response body로 변환하고, background consumer도 같은
error code를 사용할 수 있습니다. optional detail, instance, correlationId는 null일
때 JSON에서 생략됩니다.

## 소유권

| 계약 | 공통 라이브러리 책임 | 서비스 책임 |
| --- | --- | --- |
| Money | wire shape와 안전한 Java 연산 | balance, ledger, payout 정책 |
| Odds | 값 정규화와 표시 변환 | 가격 source와 drift 허용치 |
| BetSlip | 구조적 자기 일관성 | 승인, 위험 확인, wallet saga |
| Event schema | 직렬화 형식 | topic 설정, publish, consume, idempotency |
| ErrorCode | 공통 식별자와 HTTP 의미 | logging, retry, 사용자 메시지 |

공통 라이브러리에 서비스 repository, Spring component, Kafka client 또는 DB model을
추가하지 않습니다.
