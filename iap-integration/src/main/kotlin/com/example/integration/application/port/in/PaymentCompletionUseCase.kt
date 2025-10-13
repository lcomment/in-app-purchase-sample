package com.example.integration.application.port.`in`

import com.example.integration.domain.Platform
import java.time.LocalDateTime

/**
 * 지급 완료 처리 Use Case (입력 포트)
 */
interface PaymentCompletionUseCase {
    
    /**
     * 구독 지급 완료 처리 (Acknowledgment)
     */
    fun completeSubscriptionPayment(request: PaymentCompletionRequest): PaymentCompletionResult
}

/**
 * 지급 완료 요청
 */
data class PaymentCompletionRequest(
    val platform: Platform,
    val packageName: String? = null, // Google Play용
    val bundleId: String? = null,    // App Store용
    val productId: String,
    val purchaseToken: String,
    val userId: String
)

/**
 * 지급 완료 결과
 */
data class PaymentCompletionResult(
    val success: Boolean,
    val paymentId: String?,
    val completedAt: LocalDateTime?,
    val errorMessage: String? = null
)