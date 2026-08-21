package com.sportsbook.protocol.domain;

/**
 * 베팅 조합의 상태입니다(ADR-0013). {@code PENDING}은 입력, 지갑, 위험 한도 등 수락 전 검증을 나타냅니다. {@code REJECTED}는 수락 전
 * 거절, {@code CANCELLED}는 수락 뒤 사용자 또는 운영자의 취소, {@code VOIDED}는 경기 취소에 따른 자동 환불입니다(ADR-0012). 선택지별 정산
 * 결과인 {@code SettlementResult.VOID}는 이 상태를 바꾸지 않습니다.
 */
public enum BetStatus {
  PENDING,
  ACCEPTED,
  REJECTED,
  SETTLED,
  CANCELLED,
  VOIDED,
}
