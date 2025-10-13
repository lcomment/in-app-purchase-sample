package com.example.integration.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 정산 정보 도메인 모델
 */
data class Settlement(
    val id: String,
    val platform: Platform,
    val settlementDate: LocalDate,
    val totalRevenue: BigDecimal,
    val platformFee: BigDecimal,
    val netRevenue: BigDecimal,
    val currency: String,
    val paymentCount: Int,
    val refundCount: Int,
    val refundAmount: BigDecimal,
    val status: SettlementStatus,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)