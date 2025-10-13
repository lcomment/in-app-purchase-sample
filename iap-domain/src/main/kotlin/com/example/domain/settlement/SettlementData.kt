package com.example.domain.settlement

import com.example.domain.Platform
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 정산 도메인 모델
 */
data class SettlementData(
    val id: String,
    val platform: Platform,
    val settlementDate: LocalDate,
    val transactionId: String,
    val originalTransactionId: String?,
    val productId: String,
    val subscriptionId: String?,
    val eventType: SettlementEventType,
    val amount: BigDecimal,
    val currency: String,
    val platformFee: BigDecimal,
    val netAmount: BigDecimal,
    val taxAmount: BigDecimal?,
    val userId: String?,
    val countryCode: String?,
    val createdAt: LocalDateTime,
    val platformSettlementId: String? // 플랫폼별 고유 정산 ID
)