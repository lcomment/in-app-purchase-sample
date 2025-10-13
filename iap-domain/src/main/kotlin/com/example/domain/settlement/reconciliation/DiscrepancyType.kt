package com.example.domain.settlement.reconciliation

enum class DiscrepancyType(val description: String) {
    MISSING_IN_PLATFORM("플랫폼 데이터 누락"),
    MISSING_IN_INTERNAL("내부 데이터 누락"),
    AMOUNT_MISMATCH("금액 불일치"),
    EVENT_TYPE_MISMATCH("이벤트 타입 불일치"),
    TIMING_MISMATCH("시간 불일치")
}