package com.example.domain.settlement.reconciliation

enum class ReconciliationStatus(val description: String) {
    MATCHED("일치"),
    PARTIAL_MATCH("부분 일치"),
    MAJOR_DISCREPANCY("주요 불일치"),
    FAILED("실패")
}