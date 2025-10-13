package com.example.integration.domain

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 환불 정보 도메인 모델
 */
data class Refund(
    val id: String,
    val paymentId: String,
    val subscriptionId: String,
    val userId: String,
    val platform: Platform,
    val amount: BigDecimal,
    val currency: String,
    val reason: RefundReason,
    val status: RefundStatus,
    val requestedAt: LocalDateTime,
    val processedAt: LocalDateTime? = null,
    val approvedBy: String? = null,
    val customerNote: String? = null,
    val internalNote: String? = null,
    val platformRefundId: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    
    fun approve(approvedBy: String): Refund = 
        copy(status = RefundStatus.APPROVED, approvedBy = approvedBy, updatedAt = LocalDateTime.now())
    
    fun reject(reason: String): Refund = 
        copy(status = RefundStatus.REJECTED, internalNote = reason, updatedAt = LocalDateTime.now())
    
    fun process(): Refund = 
        copy(status = RefundStatus.PROCESSING, updatedAt = LocalDateTime.now())
    
    fun complete(platformRefundId: String): Refund = 
        copy(
            status = RefundStatus.COMPLETED, 
            platformRefundId = platformRefundId,
            processedAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    
    fun fail(reason: String): Refund = 
        copy(status = RefundStatus.FAILED, internalNote = reason, updatedAt = LocalDateTime.now())
    
    fun isProcessable(): Boolean = 
        status in listOf(RefundStatus.APPROVED, RefundStatus.PROCESSING)
    
    fun isCompleted(): Boolean = 
        status == RefundStatus.COMPLETED
}