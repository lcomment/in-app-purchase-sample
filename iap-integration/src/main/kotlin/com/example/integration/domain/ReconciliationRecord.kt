package com.example.integration.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 대사 처리 기록 도메인 모델
 */
data class ReconciliationRecord(
    val id: String,
    val platform: Platform,
    val reconciliationDate: LocalDate,
    val internalTransactionCount: Int,
    val externalTransactionCount: Int,
    val internalAmount: BigDecimal,
    val externalAmount: BigDecimal,
    val discrepancyCount: Int,
    val discrepancyAmount: BigDecimal,
    val currency: String,
    val status: ReconciliationStatus,
    val notes: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)