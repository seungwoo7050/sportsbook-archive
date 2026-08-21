# 이벤트 schema 변경 규칙

## Runtime 조건

서비스는 Schema Registry나 payload 안의 writer schema id 없이 Avro binary를
사용합니다. consumer는 자신이 가진 generated class schema로 payload를 읽습니다.
따라서 일반적인 Avro 호환성 판정만으로 mixed deployment의 안전성을 보장할 수
없습니다.

wire v1의 기존 record는 다음 요소를 고정합니다.

- record name과 namespace
- field name과 순서
- primitive, collection, union과 named type
- enum symbol과 순서
- default 존재 여부와 값
- timestamp logical type

기존 record에 optional field를 추가하는 방식도 현재 codec에서는 안전한 rolling
변경으로 간주하지 않습니다. 새로운 의미가 필요하면 별도 record와 topic을
추가합니다.

## Named schema

`Money`와 `SettlementResultAvro`는 여러 event가 공유하는 named type입니다.
`SettlementResultAvro`는 `BetSettled.avsc`에서 정의됩니다. 같은 fullname의 enum을
다른 schema에서 다시 정의하면 parser와 generated source가 충돌합니다.

Avro Maven Plugin은 다음 import 순서를 사용합니다.

1. `Money.avsc`
2. `BetSettled.avsc`
3. 나머지 schema

`BetResolutionRevised`는 두 named type을 이름으로 재사용합니다.

## Contract test

빌드는 다음 경계를 검증합니다.

- top-level record inventory가 정확히 14개인지 확인
- 각 generated SpecificRecord의 field 순서 확인
- required와 explicit null default 구분
- timestamp-millis logical type 확인
- named type fullname과 enum 재사용 확인
- SpecificDatumWriter/Reader binary round trip
- CRC-64-AVRO parsing canonical fingerprint 고정

Canonical fingerprint는 field의 구조적 계약을 빠르게 감지하지만 doc, default,
logical type의 모든 의미를 보존하지 않습니다. 그래서 default와 logical type을
별도 assertion으로 검증합니다.

## 변경 절차

호환 가능한 기능 추가는 다음 순서를 따릅니다.

1. 새 schema와 전용 topic/key 계약을 정의합니다.
2. schema inventory, field, named type, binary round-trip 테스트를 추가합니다.
3. orchestration에서 topic을 생성합니다.
4. consumer를 먼저 배포하고 unknown message를 받지 않는 상태를 확인합니다.
5. producer를 활성화합니다.
6. duplicate, retry, out-of-order와 replay 불변성을 검증합니다.

기존 topic을 대체해야 한다면 새 generation topic을 만들고 consumer 전환이 완료된
뒤 producer를 이동합니다. topic 이름만 유지한 채 incompatible payload로 바꾸지
않습니다.

## Generated source

`target/generated-sources/avro`는 build output입니다. source control에 넣거나 직접
수정하지 않습니다. schema 변경 후에는 반드시 clean build로 stale generated class가
남지 않음을 확인합니다.
