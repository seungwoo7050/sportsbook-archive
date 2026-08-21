# 스포츠북 공통 프로토콜

스포츠북 서비스가 동일한 값 객체, 오류 형식과 비동기 이벤트 계약을 사용하도록
제공하는 Java 17 라이브러리입니다. 실행 애플리케이션이나 서비스별 업무 정책은
포함하지 않습니다.

## 기술 구성

- Java 17
- Maven Wrapper 3.9.11
- Apache Avro 1.12
- Jackson
- JUnit 5와 AssertJ
- Spotless와 Checkstyle

## 빌드

```sh
./mvnw clean verify
```

다른 서비스를 빌드하기 전에 공통 artifact를 로컬 Maven 저장소에 설치합니다.

```sh
./mvnw clean install
```

Maven 좌표는 다음과 같습니다.

```xml
<dependency>
  <groupId>com.sportsbook</groupId>
  <artifactId>shared-protocol</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Java 계약

- `Currency`와 overflow-safe `Money`
- 정규화된 decimal `Odds`와 American/fractional 표시 변환
- `BetId`, `UserId`, `EventId`, `MarketId`, `SelectionId`
- HTTP와 Kafka 경계에서 공유하는 `IdempotencyKey`
- 단일·다중·시스템 베팅을 표현하는 `BetSlipType`
- 구조적 불변식을 보장하는 `BetSelection`과 `BetSlip`
- 공통 `ErrorCode`와 framework-neutral `ProblemDetail`

금액 잔액, 위험 한도, 배당 허용 오차, 정산 계산 같은 업무 정책은 해당 서비스를
소유자로 둡니다. 이 라이브러리는 여러 서비스가 공유해야 하는 데이터 모양과 기본
불변식만 책임집니다.

## Avro 계약

wire v1에는 다음 14개 top-level record가 있습니다.

| 영역 | record |
| --- | --- |
| 지갑 | `WalletDebited`, `WalletCredited`, `WalletDebitFailed` |
| 위험 | `RiskLimitViolated`, `RiskPatternSuspected` |
| 경기와 마켓 | `EventLifecycle`, `MarketStatusChanged`, `OddsChanged`, `MatchResult` |
| 베팅 | `BetPlacedRequested`, `BetSettled`, `BetVoided`, `BetResolutionRevised` |
| 공통 값 | Avro `Money` |

generated Java source는 `target/generated-sources/avro`에 만들어집니다. generated
파일을 직접 수정하지 말고 `src/main/avro`의 schema를 변경해야 합니다.

### 정산 결과 정정

`BetResolutionRevised`는 이미 완료된 정산 결과가 정정될 때 사용합니다.

- topic: `bet.resolution.revised.v1`
- Kafka key: `betId`
- 최초 `BetSettled`는 논리 revision 0
- 정정 revision은 bet별로 1부터 단조 증가
- 이전 결과와 payout, 새 결과와 payout을 모두 포함하는 full snapshot
- lifecycle에 의한 VOID 정정은 이 계약의 범위가 아님

producer는 wallet adjustment가 확인된 뒤 durable revision과 outbox를 함께
커밋해야 합니다. consumer는 `revisionNumber`로 중복과 역순 전달을 처리합니다.

## 문서

- [계약 소유권과 표현 경계](architecture/contract-ownership-and-representation-boundaries.md)
- [이벤트 흐름과 consumer map](architecture/event-flow-and-consumer-map.md)
- [이벤트 schema 변경 규칙](architecture/event-schema-evolution.md)
