package com.example.integration.domain

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 결제 정보 도메인 모델
 */
data class Payment(
    val id: String,
    val subscriptionId: String,
    val userId: String,
    val platform: Platform,
    val orderId: String,
    val transactionId: String,
    val purchaseToken: String,
    val productId: String,
    val amount: BigDecimal,
    val currency: String,
    val status: PaymentStatus,
    val paymentDate: LocalDateTime,
    val acknowledgmentState: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun acknowledge(): Payment = copy(acknowledgmentState = true, updatedAt = LocalDateTime.now())
    
    fun refund(): Payment = copy(status = PaymentStatus.REFUNDED, updatedAt = LocalDateTime.now())
    
    fun isSuccess(): Boolean = status == PaymentStatus.SUCCESS
}