package com.example.integration.application.port.`in`

import com.example.integration.domain.Platform
import com.example.integration.domain.Refund
import com.example.integration.domain.RefundReason
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 환불 처리 Use Case (입력 포트)
 */
interface RefundUseCase {
    
    /**
     * 환불 요청
     */
    fun requestRefund(request: RefundRequest): RefundResult
    
    /**
     * 환불 승인
     */
    fun approveRefund(refundId: String, approvedBy: String): RefundResult
    
    /**
     * 환불 거부
     */
    fun rejectRefund(refundId: String, reason: String): RefundResult
    
    /**
     * 환불 처리 실행
     */
    fun processRefund(refundId: String): RefundResult
    
    /**
     * 환불 상태 조회
     */
    fun getRefundStatus(refundId: String): Refund?
    
    /**
     * 사용자 환불 내역 조회
     */
    fun getUserRefunds(userId: String, platform: Platform? = null): List<Refund>
}

/**
 * 환불 요청
 */
data class RefundRequest(
    val paymentId: String,
    val subscriptionId: String,
    val userId: String,
    val platform: Platform,
    val amount: BigDecimal?,  // null이면 전액 환불
    val currency: String,
    val reason: RefundReason,
    val customerNote: String? = null,
    val requestedBy: String = "customer"
)

/**
 * 환불 결과
 */
data class RefundResult(
    val success: Boolean,
    val refund: Refund?,
    val errorMessage: String? = null
)