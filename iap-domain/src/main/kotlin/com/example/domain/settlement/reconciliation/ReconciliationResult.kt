package com.example.domain.settlement.reconciliation

import com.example.domain.Platform
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 정산 대사 결과
 */
data class ReconciliationResult(
    val date: LocalDate,
    val platform: Platform,
    val totalPlatformTransactions: Int,
    val totalInternalEvents: Int,
    val matchedTransactions: Int,
    val unmatchedPlatformTransactions: List<String>, // 플랫폼에만 있는 거래
    val unmatchedInternalEvents: List<String>, // 내부에만 있는 이벤트
    val discrepancies: List<ReconciliationDiscrepancy>,
    val reconciliationStatus: ReconciliationStatus,
    val processedAt: LocalDateTime
)