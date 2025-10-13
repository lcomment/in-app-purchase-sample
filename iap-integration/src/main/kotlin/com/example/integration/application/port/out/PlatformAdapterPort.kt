package com.example.integration.application.port.out

import com.example.integration.domain.Platform
import com.example.integration.domain.Subscription
import com.example.integration.domain.Payment
import java.time.LocalDate

/**
 * 플랫폼 어댑터 포트 (출력 포트)
 */
interface PlatformAdapterPort {
    
    /**
     * 플랫폼에서 구독 정보 검증
     */
    fun verifySubscription(
        productId: String,
        purchaseToken: String,
        packageName: String? = null,
        bundleId: String? = null
    ): PlatformVerificationResult
    
    /**
     * 플랫폼에 지급 완료 처리
     */
    fun acknowledgePayment(
        productId: String,
        purchaseToken: String,
        packageName: String? = null,
        bundleId: String? = null
    ): PlatformAcknowledgmentResult
    
    /**
     * 플랫폼에서 정산 데이터 조회
     */
    fun getSettlementData(
        startDate: LocalDate,
        endDate: LocalDate
    ): PlatformSettlementData
    
    /**
     * 플랫폼에서 환불 처리
     */
    fun processRefund(
        productId: String,
        purchaseToken: String,
        refundAmount: String?,
        reason: String,
        packageName: String? = null,
        bundleId: String? = null
    ): PlatformRefundResult
    
    /**
     * 지원하는 플랫폼 반환
     */
    fun getSupportedPlatform(): Platform
}

/**
 * 플랫폼 검증 결과
 */
data class PlatformVerificationResult(
    val isValid: Boolean,
    val subscription: Subscription?,
    val errorMessage: String? = null,
    val rawData: Map<String, Any> = emptyMap()
)

/**
 * 플랫폼 지급 완료 결과
 */
data class PlatformAcknowledgmentResult(
    val success: Boolean,
    val paymentId: String?,
    val errorMessage: String? = null
)

/**
 * 플랫폼 정산 데이터
 */
data class PlatformSettlementData(
    val payments: List<Payment>,
    val totalRevenue: String,
    val platformFee: String,
    val currency: String,
    val errorMessage: String? = null
)

/**
 * 플랫폼 환불 결과
 */
data class PlatformRefundResult(
    val success: Boolean,
    val platformRefundId: String?,
    val refundAmount: String?,
    val currency: String?,
    val errorMessage: String? = null,
    val estimatedProcessingTime: String? = null
)