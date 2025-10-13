package com.example.domain.settlement

import com.example.domain.Platform
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 일일 정산 요약 데이터
 */
data class DailySettlementSummary(
    val date: LocalDate,
    val platform: Platform,
    val totalTransactions: Int,
    val totalGrossAmount: BigDecimal,
    val totalPlatformFee: BigDecimal,
    val totalNetAmount: BigDecimal,
    val totalTaxAmount: BigDecimal,
    val purchaseCount: Int,
    val renewalCount: Int,
    val refundCount: Int,
    val chargebackCount: Int,
    val createdAt: LocalDateTime
)