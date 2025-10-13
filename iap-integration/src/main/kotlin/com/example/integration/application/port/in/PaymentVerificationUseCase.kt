package com.example.integration.application.port.`in`

import com.example.integration.domain.Platform
import com.example.integration.domain.Subscription
import java.math.BigDecimal

/**
 * 결제 검증 Use Case (입력 포트)
 */
interface PaymentVerificationUseCase {
    
    /**
     * 구독 결제 검증
     */
    fun verifySubscriptionPayment(request: SubscriptionVerificationRequest): SubscriptionVerificationResult
}

/**
 * 구독 검증 요청
 */
data class SubscriptionVerificationRequest(
    val platform: Platform,
    val packageName: String? = null, // Google Play용
    val bundleId: String? = null,    // App Store용
    val productId: String,
    val purchaseToken: String,
    val userId: String
)

/**
 * 구독 검증 결과
 */
data class SubscriptionVerificationResult(
    val isValid: Boolean,
    val subscription: Subscription?,
    val errorMessage: String? = null,
    val platformData: Map<String, Any> = emptyMap()
)