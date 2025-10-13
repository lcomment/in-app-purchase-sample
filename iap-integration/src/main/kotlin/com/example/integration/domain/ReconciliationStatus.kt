package com.example.integration.domain

/**
 * 대사 처리 상태
 */
enum class ReconciliationStatus {
    PENDING,
    IN_PROGRESS,
    MATCHED,
    DISCREPANCY_FOUND,
    RESOLVED,
    FAILED
}