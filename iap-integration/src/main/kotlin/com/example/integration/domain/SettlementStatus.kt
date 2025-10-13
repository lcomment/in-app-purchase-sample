package com.example.integration.domain

/**
 * 정산 상태
 */
enum class SettlementStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    DISPUTED
}